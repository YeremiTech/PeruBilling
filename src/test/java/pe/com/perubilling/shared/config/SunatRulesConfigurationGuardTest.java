package pe.com.perubilling.shared.config;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;
import pe.com.perubilling.shared.sunat.SunatRulesBaseline;

class SunatRulesConfigurationGuardTest {
    @Test
    void acceptsCompiledRegulatoryBaseline() {
        assertDoesNotThrow(() -> new SunatRulesConfigurationGuard(SunatRulesBaseline.VERSION));
    }

    @Test
    void rejectsConfigurationThatOnlyChangesTheDeclaredVersion() {
        assertThrows(IllegalStateException.class, () -> new SunatRulesConfigurationGuard("2099-01-01"));
    }
}
