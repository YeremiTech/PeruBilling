package pe.com.perubilling.shared.config;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class LocalProfileConfigurationTest {

    @Test
    void localProfileUsesValidDevelopmentSecretsWithoutGenericEnvironmentPlaceholders() throws IOException {
        try (var in = getClass().getClassLoader().getResourceAsStream("application-local.properties")) {
            assertTrue(in != null, "application-local.properties debe existir");
            String properties = new String(in.readAllBytes(), StandardCharsets.UTF_8);

            assertTrue(properties.contains("app.security.jwt.secret=bG9jYWwtZGV2LWp3dC1zZWNyZXQtMzItYnl0ZXMtbWluaW11bQ=="));
            assertTrue(properties.contains("app.security.master-key=cGVydWJpbGxpbmctbG9jYWwtbWFzdGVyLWtleS0zMiE="));
            assertTrue(properties.contains("app.security.api-key-pepper=local-api-key-pepper-development-only-32plus"));

            assertFalse(properties.contains("${JWT_SECRET:"));
            assertFalse(properties.contains("${MASTER_KEY:"));
            assertFalse(properties.contains("${API_KEY_PEPPER:"));
        }
    }
    @Test
    void localProfileIsDefaultWhenNoExplicitProfileIsProvided() throws IOException {
        try (var in = getClass().getClassLoader().getResourceAsStream("application.properties")) {
            assertTrue(in != null, "application.properties debe existir");
            String properties = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            assertTrue(properties.contains("spring.profiles.default=local"));
        }
    }

}
