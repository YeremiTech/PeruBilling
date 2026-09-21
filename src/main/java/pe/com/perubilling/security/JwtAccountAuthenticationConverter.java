package pe.com.perubilling.security;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import pe.com.perubilling.identity.infrastructure.UserAccountRepository;
import pe.com.perubilling.tenant.domain.TenantStatus;
import pe.com.perubilling.tenant.infrastructure.TenantRepository;

/**
 * Revalida el estado operativo asociado a un JWT en cada request.
 *
 * <p>El token sigue aportando identidad y expiración criptográficamente verificadas,
 * pero las autorizaciones se obtienen de la cuenta persistida. Esto hace efectivas
 * de inmediato la suspensión del tenant, la deshabilitación/bloqueo del usuario y
 * los cambios de rol, sin esperar al vencimiento del access token.</p>
 */
@Component
public class JwtAccountAuthenticationConverter implements Converter<Jwt, AbstractAuthenticationToken> {
    private final UserAccountRepository users;
    private final TenantRepository tenants;

    public JwtAccountAuthenticationConverter(UserAccountRepository users, TenantRepository tenants) {
        this.users = users;
        this.tenants = tenants;
    }

    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        UUID userId = uuid(jwt.getSubject(), "sub");
        UUID tenantId = uuid(jwt.getClaimAsString("tenant_id"), "tenant_id");

        var user = users.findByIdAndTenantId(userId, tenantId)
                .orElseThrow(this::inactiveAuthentication);

        Instant now = Instant.now();
        if (!user.isEnabled() || (user.getLockedUntil() != null && user.getLockedUntil().isAfter(now))) {
            throw inactiveAuthentication();
        }

        boolean tenantActive = tenants.findById(tenantId)
                .map(tenant -> tenant.getStatus() == TenantStatus.ACTIVE)
                .orElse(false);
        if (!tenantActive) {
            throw inactiveAuthentication();
        }

        var authorities = List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole().name()));
        return new JwtAuthenticationToken(jwt, authorities, user.getEmail());
    }

    private UUID uuid(String value, String claim) {
        if (value == null || value.isBlank()) {
            throw new BadCredentialsException("JWT sin claim requerido: " + claim);
        }
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException exception) {
            throw new BadCredentialsException("JWT contiene un claim de identidad inválido", exception);
        }
    }

    private BadCredentialsException inactiveAuthentication() {
        return new BadCredentialsException("La identidad asociada al JWT ya no está habilitada");
    }
}
