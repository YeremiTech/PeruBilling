package pe.com.perubilling.identity.application;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;
import pe.com.perubilling.shared.domain.BusinessException;

class PasswordPolicyTest {
    private final PasswordPolicy policy = new PasswordPolicy();

    @Test
    void acceptsStrongPassword() {
        assertDoesNotThrow(() -> policy.validate("Nube!Segura2026", "admin@example.test"));
    }

    @Test
    void rejectsWeakAndEmailDerivedPasswords() {
        assertThrows(BusinessException.class, () -> policy.validate("password123", "admin@example.test"));
        assertThrows(BusinessException.class, () -> policy.validate("Admin!Seguro2026", "admin@example.test"));
    }
}
