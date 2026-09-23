package pe.com.perubilling.identity.application;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import pe.com.perubilling.identity.api.UpdateUserRequest;
import pe.com.perubilling.identity.domain.UserAccountEntity;
import pe.com.perubilling.identity.domain.UserRole;
import pe.com.perubilling.identity.infrastructure.UserAccountRepository;
import pe.com.perubilling.shared.domain.BusinessException;
import pe.com.perubilling.shared.security.TenantContext;

class UserManagementServiceTest {
    @Test
    void refusesToDisableLastActiveAdmin() {
        UUID tenantId = UUID.randomUUID();
        UUID adminId = UUID.randomUUID();
        var users = mock(UserAccountRepository.class);
        var tenant = mock(TenantContext.class);
        when(tenant.requireTenantId()).thenReturn(tenantId);
        when(tenant.currentUserIdOrNull()).thenReturn(null);
        when(users.findByIdAndTenantId(adminId, tenantId)).thenReturn(Optional.of(admin(adminId, tenantId)));
        when(users.countByTenantIdAndEnabledTrueAndRole(tenantId, UserRole.ADMIN)).thenReturn(1L);

        var service = new UserManagementService(users, tenant, new BCryptPasswordEncoder(4), new PasswordPolicy());
        assertThrows(BusinessException.class, () -> service.update(adminId, new UpdateUserRequest(false, null)));
    }

    @Test
    void allowsAdminChangeWhenAnotherActiveAdminRemains() {
        UUID tenantId = UUID.randomUUID();
        UUID adminId = UUID.randomUUID();
        var users = mock(UserAccountRepository.class);
        var tenant = mock(TenantContext.class);
        when(tenant.requireTenantId()).thenReturn(tenantId);
        when(tenant.currentUserIdOrNull()).thenReturn(null);
        when(users.findByIdAndTenantId(adminId, tenantId)).thenReturn(Optional.of(admin(adminId, tenantId)));
        when(users.countByTenantIdAndEnabledTrueAndRole(tenantId, UserRole.ADMIN)).thenReturn(2L);

        var service = new UserManagementService(users, tenant, new BCryptPasswordEncoder(4), new PasswordPolicy());
        assertDoesNotThrow(() -> service.update(adminId, new UpdateUserRequest(false, null)));
    }

    private UserAccountEntity admin(UUID id, UUID tenantId) {
        var user = new UserAccountEntity();
        user.setId(id);
        user.setTenantId(tenantId);
        user.setEmail("admin@example.test");
        user.setPasswordHash("unused");
        user.setRole(UserRole.ADMIN);
        user.setEnabled(true);
        return user;
    }
}
