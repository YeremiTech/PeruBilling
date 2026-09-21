package pe.com.perubilling.cpe.validation;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;
import pe.com.perubilling.issuer.domain.SunatEnvironment;

class SunatXsdEnvironmentGuardTest {
    @Test
    void localEnvironmentCanRunWithXsdDisabled() {
        var validator = new SunatXsdValidator(false, false, "./missing-xsd");
        assertDoesNotThrow(() -> validator.requireForEnvironment(SunatEnvironment.LOCAL));
    }

    @Test
    void betaAndProductionRequireXsdValidation() {
        var validator = new SunatXsdValidator(false, false, "./missing-xsd");
        assertThrows(IllegalStateException.class,
                () -> validator.requireForEnvironment(SunatEnvironment.BETA));
        assertThrows(IllegalStateException.class,
                () -> validator.requireForEnvironment(SunatEnvironment.PRODUCTION));
    }
}
