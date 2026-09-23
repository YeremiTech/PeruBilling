package pe.com.perubilling.identity.application;

import java.util.List;
import java.util.UUID;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.com.perubilling.identity.api.ChangePasswordRequest;
import pe.com.perubilling.identity.api.CreateUserRequest;
import pe.com.perubilling.identity.api.UpdateUserRequest;
import pe.com.perubilling.identity.api.UserResponse;
import pe.com.perubilling.identity.domain.UserAccountEntity;
import pe.com.perubilling.identity.infrastructure.UserAccountRepository;
import pe.com.perubilling.shared.domain.BusinessException;
import pe.com.perubilling.shared.security.TenantContext;

@Service
public class UserManagementService {
    private final UserAccountRepository users;
    private final TenantContext tenant;
    private final PasswordEncoder passwords;
    private final PasswordPolicy policy;

    public UserManagementService(UserAccountRepository users, TenantContext tenant,
                                 PasswordEncoder passwords, PasswordPolicy policy) {
        this.users=users; this.tenant=tenant; this.passwords=passwords; this.policy=policy;
    }

    @Transactional(readOnly = true)
    public List<UserResponse> list() {
        return users.findAllByTenantIdOrderByEmailAsc(tenant.requireTenantId()).stream().map(this::map).toList();
    }

    @Transactional
    public UserResponse create(CreateUserRequest request) {
        UUID tenantId = tenant.requireTenantId();
        String email = request.email().trim().toLowerCase();
        if (users.existsByEmailIgnoreCase(email)) {
            throw BusinessException.conflict("EMAIL_ALREADY_EXISTS", "El correo ya está registrado");
        }
        policy.validate(request.password(), email);
        UserAccountEntity user = new UserAccountEntity();
        user.setTenantId(tenantId);
        user.setEmail(email);
        user.setPasswordHash(passwords.encode(request.password()));
        user.setRole(request.role());
        user.setEnabled(true);
        return map(users.save(user));
    }

    @Transactional
    public UserResponse update(UUID userId, UpdateUserRequest request) {
        UUID tenantId=tenant.requireTenantId();
        UserAccountEntity user=users.findByIdAndTenantId(userId,tenantId)
                .orElseThrow(()->BusinessException.notFound("USER_NOT_FOUND","Usuario no encontrado"));
        UUID current=tenant.currentUserIdOrNull();
        if (current != null && current.equals(userId)) {
            if (Boolean.FALSE.equals(request.enabled())) {
                throw BusinessException.conflict("SELF_DISABLE_FORBIDDEN","No puede deshabilitar su propia cuenta");
            }
            if (request.role()!=null && request.role()!=user.getRole()) {
                throw BusinessException.conflict("SELF_ROLE_CHANGE_FORBIDDEN","No puede cambiar su propio rol");
            }
        }
        boolean resultingEnabled = request.enabled() == null ? user.isEnabled() : request.enabled();
        var resultingRole = request.role() == null ? user.getRole() : request.role();
        boolean removesActiveAdmin = user.isEnabled()
                && user.getRole() == pe.com.perubilling.identity.domain.UserRole.ADMIN
                && (!resultingEnabled || resultingRole != pe.com.perubilling.identity.domain.UserRole.ADMIN);
        if (removesActiveAdmin
                && users.countByTenantIdAndEnabledTrueAndRole(tenantId, pe.com.perubilling.identity.domain.UserRole.ADMIN) <= 1) {
            throw BusinessException.conflict(
                    "LAST_ADMIN_REQUIRED",
                    "El tenant debe conservar al menos un administrador activo");
        }
        user.setEnabled(resultingEnabled);
        user.setRole(resultingRole);
        if (!user.isEnabled()) {
            user.setFailedLoginCount(0);
            user.setLockedUntil(null);
        }
        return map(user);
    }

    @Transactional
    public void changeOwnPassword(ChangePasswordRequest request) {
        UUID tenantId=tenant.requireTenantId();
        UUID userId=tenant.currentUserIdOrNull();
        if (userId==null) throw BusinessException.unauthorized("USER_AUTH_REQUIRED", "Se requiere autenticación de usuario");
        UserAccountEntity user=users.findByIdAndTenantId(userId,tenantId)
                .orElseThrow(()->BusinessException.notFound("USER_NOT_FOUND","Usuario no encontrado"));
        if (!passwords.matches(request.currentPassword(),user.getPasswordHash())) {
            throw BusinessException.badRequest("CURRENT_PASSWORD_INVALID","La contraseña actual no es correcta");
        }
        policy.validate(request.newPassword(),user.getEmail());
        if (passwords.matches(request.newPassword(),user.getPasswordHash())) {
            throw BusinessException.badRequest("PASSWORD_REUSE","La nueva contraseña debe ser diferente a la actual");
        }
        user.setPasswordHash(passwords.encode(request.newPassword()));
        user.setFailedLoginCount(0);
        user.setLockedUntil(null);
    }

    private UserResponse map(UserAccountEntity user) {
        return new UserResponse(user.getId(),user.getEmail(),user.getRole(),user.isEnabled(),
                user.getLastLoginAt(),user.getLockedUntil(),user.getCreatedAt());
    }
}
