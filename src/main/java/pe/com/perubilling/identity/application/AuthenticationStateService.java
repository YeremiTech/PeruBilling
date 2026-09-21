package pe.com.perubilling.identity.application;

import java.time.Duration;
import java.time.Instant;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import pe.com.perubilling.identity.domain.UserAccountEntity;
import pe.com.perubilling.identity.infrastructure.UserAccountRepository;
import pe.com.perubilling.tenant.domain.TenantStatus;
import pe.com.perubilling.tenant.infrastructure.TenantRepository;

@Service
public class AuthenticationStateService {
    private static final int MAX_FAILURES = 5;
    private static final Duration LOCK_DURATION = Duration.ofMinutes(15);
    // Hash BCrypt de una contraseña ficticia. Se usa solamente para igualar el coste
    // de verificación cuando el correo no existe y reducir enumeración por timing.
    private static final String DUMMY_PASSWORD_HASH =
            "$2a$12$DPWAtD6TFkyrI4xtjuMfXumHrZSPUOgauM/fltD25SyAjmEOCWkI2";

    private final UserAccountRepository users;
    private final PasswordEncoder passwordEncoder;
    private final TenantRepository tenants;

    public AuthenticationStateService(UserAccountRepository users, PasswordEncoder passwordEncoder, TenantRepository tenants) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.tenants = tenants;
    }

    /**
     * Confirma el estado de autenticación en una transacción independiente y nunca
     * lanza la excepción HTTP que responde al cliente. De esta forma los contadores
     * de fallos y locked_until quedan confirmados aunque el login termine en 401/429.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public AuthenticationResult authenticate(String email, String rawPassword, Instant now) {
        var user = users.findByEmailForAuthentication(email.trim()).orElse(null);
        if (user == null) {
            passwordEncoder.matches(rawPassword, DUMMY_PASSWORD_HASH);
            return AuthenticationResult.invalid();
        }
        if (!user.isEnabled()) {
            passwordEncoder.matches(rawPassword, user.getPasswordHash());
            return AuthenticationResult.invalid();
        }
        boolean tenantActive = tenants.findById(user.getTenantId())
                .map(t -> t.getStatus() == TenantStatus.ACTIVE)
                .orElse(false);
        if (!tenantActive) {
            passwordEncoder.matches(rawPassword, user.getPasswordHash());
            return AuthenticationResult.invalid();
        }

        if (user.getLockedUntil() != null && user.getLockedUntil().isAfter(now)) {
            return AuthenticationResult.locked();
        }

        if (!passwordEncoder.matches(rawPassword, user.getPasswordHash())) {
            int failures = user.getFailedLoginCount() + 1;
            if (failures >= MAX_FAILURES) {
                user.setFailedLoginCount(0);
                user.setLockedUntil(now.plus(LOCK_DURATION));
                return AuthenticationResult.lockedAfterFailure();
            }
            user.setFailedLoginCount(failures);
            return AuthenticationResult.invalid();
        }

        user.setFailedLoginCount(0);
        user.setLockedUntil(null);
        user.setLastLoginAt(now);
        return AuthenticationResult.success(user);
    }

    public enum Outcome {
        SUCCESS,
        INVALID,
        LOCKED
    }

    public record AuthenticationResult(Outcome outcome, UserAccountEntity user) {
        public static AuthenticationResult success(UserAccountEntity user) {
            return new AuthenticationResult(Outcome.SUCCESS, user);
        }

        public static AuthenticationResult invalid() {
            return new AuthenticationResult(Outcome.INVALID, null);
        }

        public static AuthenticationResult locked() {
            return new AuthenticationResult(Outcome.LOCKED, null);
        }

        public static AuthenticationResult lockedAfterFailure() {
            return locked();
        }
    }
}
