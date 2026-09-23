package pe.com.perubilling.access.application;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.Arrays;
import java.util.Base64;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.com.perubilling.access.api.ApiKeyCreatedResponse;
import pe.com.perubilling.access.api.ApiKeyResponse;
import pe.com.perubilling.access.api.CreateApiKeyRequest;
import pe.com.perubilling.access.domain.ApiKeyEntity;
import pe.com.perubilling.access.infrastructure.ApiKeyRepository;
import pe.com.perubilling.shared.crypto.HashingService;
import pe.com.perubilling.shared.domain.BusinessException;
import pe.com.perubilling.shared.security.TenantContext;

@Service
public class ApiKeyService {
    private static final Set<String> ALLOWED_SCOPES = Set.of("DOCUMENT_READ", "DOCUMENT_WRITE", "ISSUER_READ", "WEBHOOK_WRITE", "OPERATIONS_READ");
    private final SecureRandom random = new SecureRandom();
    private final ApiKeyRepository repository;
    private final TenantContext tenantContext;
    private final HashingService hashing;
    private final String pepper;

    public ApiKeyService(ApiKeyRepository repository, TenantContext tenantContext, HashingService hashing,
                         @Value("${app.security.api-key-pepper:}") String pepper) {
        this.repository = repository;
        this.tenantContext = tenantContext;
        this.hashing = hashing;
        this.pepper = pepper;
        if (pepper == null || pepper.trim().length() < 32) {
            throw new IllegalStateException("API_KEY_PEPPER debe tener al menos 32 caracteres aleatorios");
        }
    }

    @Transactional
    public ApiKeyCreatedResponse create(CreateApiKeyRequest request) {
        UUID tenantId = tenantContext.requireTenantId();
        Set<String> scopes = request.scopes().stream().map(v -> v.toUpperCase(Locale.ROOT)).collect(Collectors.toSet());
        if (!ALLOWED_SCOPES.containsAll(scopes)) throw BusinessException.badRequest("INVALID_SCOPES", "Se solicitaron scopes no permitidos");
        byte[] secret = new byte[32];
        random.nextBytes(secret);
        String raw = "pb_live_" + Base64.getUrlEncoder().withoutPadding().encodeToString(secret);
        String prefix = raw.substring(0, Math.min(raw.length(), 20));
        ApiKeyEntity entity = new ApiKeyEntity();
        entity.setTenantId(tenantId);
        entity.setName(request.name().trim());
        entity.setPrefix(prefix);
        entity.setSecretHash(hashing.sha256(raw + ":" + pepper));
        entity.setScopes(scopes.stream().sorted().collect(Collectors.joining(",")));
        if (request.expiresAt() != null && !request.expiresAt().isAfter(Instant.now())) {
            throw BusinessException.badRequest("INVALID_API_KEY_EXPIRATION", "expiresAt debe estar en el futuro");
        }
        entity.setExpiresAt(request.expiresAt());
        repository.save(entity);
        return new ApiKeyCreatedResponse(entity.getId(), entity.getName(), raw, prefix, scopes, entity.getExpiresAt());
    }

    @Transactional(readOnly = true)
    public java.util.List<ApiKeyResponse> list() {
        return repository.findAllByTenantIdOrderByCreatedAtDesc(tenantContext.requireTenantId()).stream().map(this::toResponse).toList();
    }

    @Transactional
    public void revoke(UUID id) {
        ApiKeyEntity entity = repository.findByIdAndTenantId(id, tenantContext.requireTenantId())
                .orElseThrow(() -> BusinessException.notFound("API_KEY_NOT_FOUND", "API Key no encontrada"));
        entity.setRevokedAt(Instant.now());
    }

    private ApiKeyResponse toResponse(ApiKeyEntity entity) {
        Set<String> scopes = Arrays.stream(entity.getScopes().split(","))
                .filter(scope -> !scope.isBlank())
                .collect(Collectors.toSet());
        return new ApiKeyResponse(
                entity.getId(),
                entity.getName(),
                entity.getPrefix(),
                scopes,
                entity.getLastUsedAt(),
                entity.getExpiresAt(),
                entity.getRevokedAt(),
                entity.getCreatedAt());
    }
}
