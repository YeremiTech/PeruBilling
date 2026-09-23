package pe.com.perubilling.shared.config;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;

import org.junit.jupiter.api.Test;
import org.springframework.boot.ApplicationArguments;
import org.springframework.mock.env.MockEnvironment;

class ProductionConfigurationGuardTest {
    @Test
    void disabledGuardDoesNotEnforceProductionProfile() {
        var environment = new MockEnvironment();
        var guard = guard(environment, false, false, true, true, false, false, "database", "https://billing.example.com");
        assertDoesNotThrow(() -> guard.run(mock(ApplicationArguments.class)));
    }

    @Test
    void enabledGuardRequiresProdProfile() {
        var environment = secureEnvironment();
        var guard = guard(environment, true, false, true, true, false, false, "database", "https://billing.example.com");
        assertThrows(IllegalStateException.class, () -> guard.run(mock(ApplicationArguments.class)));
    }

    @Test
    void secureProductionConfigurationPasses() {
        var environment = secureEnvironment();
        environment.setActiveProfiles("prod");
        var guard = guard(environment, true, false, true, true, false, false, "database", "https://billing.example.com");
        assertDoesNotThrow(() -> guard.run(mock(ApplicationArguments.class)));
    }

    @Test
    void productionRejectsFilesystemAndHttpPublicBaseUrl() {
        var environment = secureEnvironment();
        environment.setActiveProfiles("prod");
        var filesystem = guard(environment, true, false, true, true, false, false, "filesystem", "https://billing.example.com");
        var http = guard(environment, true, false, true, true, false, false, "database", "http://billing.example.com");
        assertThrows(IllegalStateException.class, () -> filesystem.run(mock(ApplicationArguments.class)));
        assertThrows(IllegalStateException.class, () -> http.run(mock(ApplicationArguments.class)));
    }

    @Test
    void productionRejectsDisabledArtifactChecksum() {
        var environment = secureEnvironment();
        environment.setActiveProfiles("prod");
        environment.setProperty("app.artifacts.require-checksum", "false");
        var guard = guard(environment, true, false, true, true, false, false, "database", "https://billing.example.com");
        assertThrows(IllegalStateException.class, () -> guard.run(mock(ApplicationArguments.class)));
    }

    @Test
    void productionRejectsDefaultDatabasePassword() {
        var environment = secureEnvironment();
        environment.setActiveProfiles("prod");
        environment.setProperty("spring.datasource.password", "change_me");
        var guard = guard(environment, true, false, true, true, false, false, "database", "https://billing.example.com");
        assertThrows(IllegalStateException.class, () -> guard.run(mock(ApplicationArguments.class)));
    }

    @Test
    void productionRejectsInsecureCorsOrigin() {
        var environment = secureEnvironment();
        environment.setActiveProfiles("prod");
        environment.setProperty("app.security.cors.allowed-origins", "http://app.example.com");
        var guard = guard(environment, true, false, true, true, false, false, "database", "https://billing.example.com");
        assertThrows(IllegalStateException.class, () -> guard.run(mock(ApplicationArguments.class)));
    }

    @Test
    void productionRejectsNonOfficialSunatEndpoint() {
        var environment = secureEnvironment();
        environment.setActiveProfiles("prod");
        environment.setProperty("app.sunat.production-url", "https://example.com/billService");
        var guard = guard(environment, true, false, true, true, false, false, "database", "https://billing.example.com");
        assertThrows(IllegalStateException.class, () -> guard.run(mock(ApplicationArguments.class)));
    }

    private MockEnvironment secureEnvironment() {
        var environment = new MockEnvironment();
        environment.setProperty("app.artifacts.require-checksum", "true");
        environment.setProperty("spring.datasource.url", "jdbc:postgresql://db.internal:5432/perubilling");
        environment.setProperty("spring.datasource.username", "perubilling_app");
        environment.setProperty("spring.datasource.password", "strong-db-password-2026");
        environment.setProperty("app.security.cors.allowed-origins", "https://app.example.com,https://admin.example.com");
        environment.setProperty("app.sunat.production-url", "https://e-factura.sunat.gob.pe/ol-ti-itcpfegem/billService");
        environment.setProperty("app.sunat.consult-url", "https://e-factura.sunat.gob.pe/ol-it-wsconscpegem/billConsultService");
        return environment;
    }

    private ProductionConfigurationGuard guard(
            MockEnvironment environment,
            boolean enabled,
            boolean bootstrap,
            boolean xsd,
            boolean failFast,
            boolean apiDocs,
            boolean swagger,
            String backend,
            String publicBaseUrl) {
        return new ProductionConfigurationGuard(
                environment, enabled, bootstrap, xsd, failFast, apiDocs, swagger, backend, publicBaseUrl);
    }
}
