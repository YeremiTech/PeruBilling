package pe.com.perubilling.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.oauth2.jwt.Jwt;
import pe.com.perubilling.identity.domain.UserAccountEntity;
import pe.com.perubilling.identity.domain.UserRole;
import pe.com.perubilling.identity.infrastructure.UserAccountRepository;
import pe.com.perubilling.tenant.domain.TenantEntity;
import pe.com.perubilling.tenant.domain.TenantStatus;
import pe.com.perubilling.tenant.infrastructure.TenantRepository;

@ExtendWith(MockitoExtension.class)
class JwtAccountAuthenticationConverterTest {
    @Mock UserAccountRepository users;
    @Mock TenantRepository tenants;

    JwtAccountAuthenticationConverter converter;
    UUID tenantId;
    UUID userId;
    UserAccountEntity user;
    TenantEntity tenant;

    @BeforeEach
    void setUp() {
        converter = new JwtAccountAuthenticationConverter(users, tenants);
        tenantId = UUID.randomUUID();
        userId = UUID.randomUUID();

        user = new UserAccountEntity();
        user.setId(userId);
        user.setTenantId(tenantId);
        user.setEmail("operator@example.test");
        user.setPasswordHash("unused");
        user.setRole(UserRole.OPERATOR);
        user.setEnabled(true);

        tenant = new TenantEntity();
        tenant.setId(tenantId);
        tenant.setName("Tenant");
        tenant.setSlug("tenant");
        tenant.setStatus(TenantStatus.ACTIVE);
    }

    @Test
    void usesCurrentPersistedRoleInsteadOfStaleRoleClaim() {
        user.setRole(UserRole.VIEWER);
        when(users.findByIdAndTenantId(userId, tenantId)).thenReturn(Optional.of(user));
        when(tenants.findById(tenantId)).thenReturn(Optional.of(tenant));

        var authentication = converter.convert(jwt(UserRole.ADMIN));

        assertEquals("operator@example.test", authentication.getName());
        assertEquals(1, authentication.getAuthorities().size());
        assertEquals("ROLE_VIEWER", authentication.getAuthorities().iterator().next().getAuthority());
    }

    @Test
    void rejectsPreviouslyIssuedTokenWhenTenantIsSuspended() {
        tenant.setStatus(TenantStatus.SUSPENDED);
        when(users.findByIdAndTenantId(userId, tenantId)).thenReturn(Optional.of(user));
        when(tenants.findById(tenantId)).thenReturn(Optional.of(tenant));

        assertThrows(BadCredentialsException.class, () -> converter.convert(jwt(UserRole.OPERATOR)));
    }

    @Test
    void rejectsPreviouslyIssuedTokenWhenUserIsDisabledOrLocked() {
        when(users.findByIdAndTenantId(userId, tenantId)).thenReturn(Optional.of(user));

        user.setEnabled(false);
        assertThrows(BadCredentialsException.class, () -> converter.convert(jwt(UserRole.OPERATOR)));

        user.setEnabled(true);
        user.setLockedUntil(Instant.now().plusSeconds(60));
        assertThrows(BadCredentialsException.class, () -> converter.convert(jwt(UserRole.OPERATOR)));
    }

    @Test
    void rejectsTokenWhenPersistedUserNoLongerExistsForTenant() {
        when(users.findByIdAndTenantId(userId, tenantId)).thenReturn(Optional.empty());

        assertThrows(BadCredentialsException.class, () -> converter.convert(jwt(UserRole.OPERATOR)));
    }

    private Jwt jwt(UserRole staleRole) {
        Instant now = Instant.now();
        return new Jwt(
                "token",
                now,
                now.plusSeconds(900),
                Map.of("alg", "HS256"),
                Map.of(
                        "sub", userId.toString(),
                        "tenant_id", tenantId.toString(),
                        "email", "old@example.test",
                        "roles", java.util.List.of(staleRole.name())));
    }
}
