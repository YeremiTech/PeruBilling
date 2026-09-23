package pe.com.perubilling.shared.config;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class SecureDefaultConfigurationTest {
    @Test
    void defaultConfigurationDoesNotActivateLocalProfileOrBootstrap() throws IOException {
        String properties = resource("/application.properties");
        assertFalse(properties.contains("spring.profiles.default=local"));
        assertTrue(properties.contains("app.bootstrap.enabled=${BOOTSTRAP_ENABLED:false}"));
        assertTrue(properties.contains("app.security.jwt.secret=${JWT_SECRET:}"));
        assertTrue(properties.contains("app.security.master-key=${MASTER_KEY:}"));
    }

    @Test
    void betaForcesXsdAndFailFast() throws IOException {
        String properties = resource("/application-beta.properties");
        assertTrue(properties.contains("app.sunat.xsd.enabled=true"));
        assertTrue(properties.contains("app.sunat.xsd.fail-fast=true"));
    }

    @Test
    void productionDatabaseCredentialsHaveNoKnownFallback() throws IOException {
        String properties = resource("/application-prod.properties");
        assertTrue(properties.contains("spring.datasource.url=${DATABASE_URL}"));
        assertTrue(properties.contains("spring.datasource.username=${DATABASE_USERNAME}"));
        assertTrue(properties.contains("spring.datasource.password=${DATABASE_PASSWORD}"));
        assertFalse(properties.contains("DATABASE_PASSWORD:perubilling"));
    }

    @Test
    void productionEnablesGuardAndSharedArtifactBackend() throws IOException {
        String properties = resource("/application-prod.properties");
        assertTrue(properties.contains("app.production.guard.enabled=${PRODUCTION_GUARD_ENABLED:true}"));
        assertTrue(properties.contains("app.artifacts.backend=${ARTIFACTS_BACKEND:database}"));
        assertTrue(properties.contains("springdoc.api-docs.enabled=false"));
        assertTrue(properties.contains("springdoc.swagger-ui.enabled=false"));
    }

    private String resource(String name) throws IOException {
        try (var input = getClass().getResourceAsStream(name)) {
            assertTrue(input != null, name + " debe existir");
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
