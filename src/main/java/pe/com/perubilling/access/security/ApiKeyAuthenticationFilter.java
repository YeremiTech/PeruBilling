package pe.com.perubilling.access.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import pe.com.perubilling.access.infrastructure.ApiKeyRepository;
import pe.com.perubilling.shared.crypto.HashingService;
import pe.com.perubilling.shared.security.ApiKeyPrincipal;
import pe.com.perubilling.tenant.domain.TenantStatus;
import pe.com.perubilling.tenant.infrastructure.TenantRepository;

@Component
public class ApiKeyAuthenticationFilter extends OncePerRequestFilter {
    private final ApiKeyRepository repository;
    private final TenantRepository tenants;
    private final HashingService hashingService;
    private final String pepper;
    private final Duration lastUsedWriteInterval;

    public ApiKeyAuthenticationFilter(
            ApiKeyRepository repository,
            TenantRepository tenants,
            HashingService hashingService,
            @Value("${app.security.api-key-pepper:}") String pepper,
            @Value("${app.security.api-key-last-used-write-interval-seconds:300}") long lastUsedWriteIntervalSeconds) {
        this.repository = repository;
        this.tenants = tenants;
        this.hashingService = hashingService;
        this.pepper = pepper;
        if (pepper == null || pepper.trim().length() < 32) {
            throw new IllegalStateException("API_KEY_PEPPER debe tener al menos 32 caracteres aleatorios");
        }
        this.lastUsedWriteInterval = Duration.ofSeconds(Math.max(60, lastUsedWriteIntervalSeconds));
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        String raw = request.getHeader("X-API-Key");
        if (raw != null && !raw.isBlank() && SecurityContextHolder.getContext().getAuthentication() == null) {
            authenticate(raw);
        }
        filterChain.doFilter(request, response);
    }

    private void authenticate(String raw) {
        String hash = hashingService.sha256(raw + ":" + pepper);
        repository.findBySecretHashAndRevokedAtIsNull(hash).ifPresent(entity -> {
            Instant now = Instant.now();
            boolean expired = entity.getExpiresAt() != null && !entity.getExpiresAt().isAfter(now);
            boolean tenantActive = tenants.findById(entity.getTenantId())
                    .map(tenant -> tenant.getStatus() == TenantStatus.ACTIVE)
                    .orElse(false);
            if (expired || !tenantActive) {
                return;
            }

            Set<String> scopes = Arrays.stream(entity.getScopes().split(","))
                    .map(String::trim)
                    .filter(scope -> !scope.isBlank())
                    .collect(Collectors.toUnmodifiableSet());
            var authorities = scopes.stream()
                    .map(scope -> new SimpleGrantedAuthority("SCOPE_" + scope))
                    .toList();
            var principal = new ApiKeyPrincipal(entity.getId(), entity.getTenantId(), entity.getName(), scopes);
            SecurityContextHolder.getContext().setAuthentication(
                    UsernamePasswordAuthenticationToken.authenticated(principal, null, authorities));

            repository.touchLastUsedAt(entity.getId(), now, now.minus(lastUsedWriteInterval));
        });
    }
}
