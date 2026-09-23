package pe.com.perubilling.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

class SecurityConfigurationContractTest {
    private final SecurityConfig config = new SecurityConfig();

    @Test
    void corsUsesExplicitAllowlistAndNeverWildcard() {
        var source = config.corsConfigurationSource("https://app.example.com, https://admin.example.com");
        var request = new MockHttpServletRequest("GET", "/api/v1/documents");
        var cors = source.getCorsConfiguration(request);
        assertEquals(List.of("https://app.example.com", "https://admin.example.com"), cors.getAllowedOrigins());
        assertEquals(Boolean.FALSE, cors.getAllowCredentials());
        assertThrows(IllegalStateException.class, () -> config.corsConfigurationSource("*"));
    }
}
