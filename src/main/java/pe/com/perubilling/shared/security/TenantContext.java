package pe.com.perubilling.shared.security;

import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;
import pe.com.perubilling.shared.domain.BusinessException;

@Component
public class TenantContext {
    public UUID requireTenantId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new BusinessException(HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED", "Autenticación requerida");
        }
        Object principal = authentication.getPrincipal();
        if (principal instanceof ApiKeyPrincipal apiKeyPrincipal) return apiKeyPrincipal.tenantId();
        if (principal instanceof Jwt jwt) {
            String tenant = jwt.getClaimAsString("tenant_id");
            if (tenant != null) return UUID.fromString(tenant);
        }
        throw new BusinessException(HttpStatus.UNAUTHORIZED, "TENANT_MISSING", "No se pudo resolver el tenant de la autenticación");
    }

    public UUID currentUserIdOrNull() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null) return null;
        if (authentication.getPrincipal() instanceof Jwt jwt) {
            try { return UUID.fromString(jwt.getSubject()); } catch (Exception ignored) { return null; }
        }
        return null;
    }
}
