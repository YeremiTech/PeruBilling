package pe.com.perubilling.shared.config;

import java.net.URI;
import java.util.Arrays;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

@Component
public class ProductionConfigurationGuard implements ApplicationRunner {
    private final Environment environment;
    private final boolean enabled;
    private final boolean bootstrapEnabled;
    private final boolean xsdEnabled;
    private final boolean xsdFailFast;
    private final boolean apiDocsEnabled;
    private final boolean swaggerEnabled;
    private final String artifactBackend;
    private final String publicBaseUrl;

    public ProductionConfigurationGuard(
            Environment environment,
            @Value("${app.production.guard.enabled:false}") boolean enabled,
            @Value("${app.bootstrap.enabled:false}") boolean bootstrapEnabled,
            @Value("${app.sunat.xsd.enabled:false}") boolean xsdEnabled,
            @Value("${app.sunat.xsd.fail-fast:false}") boolean xsdFailFast,
            @Value("${springdoc.api-docs.enabled:true}") boolean apiDocsEnabled,
            @Value("${springdoc.swagger-ui.enabled:true}") boolean swaggerEnabled,
            @Value("${app.artifacts.backend:filesystem}") String artifactBackend,
            @Value("${app.public.base-url:http://localhost:8080}") String publicBaseUrl) {
        this.environment = environment;
        this.enabled = enabled;
        this.bootstrapEnabled = bootstrapEnabled;
        this.xsdEnabled = xsdEnabled;
        this.xsdFailFast = xsdFailFast;
        this.apiDocsEnabled = apiDocsEnabled;
        this.swaggerEnabled = swaggerEnabled;
        this.artifactBackend = artifactBackend;
        this.publicBaseUrl = publicBaseUrl;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!enabled) {
            return;
        }
        boolean prodProfile = Arrays.asList(environment.getActiveProfiles()).contains("prod");
        if (!prodProfile) {
            throw new IllegalStateException(
                    "PRODUCTION_GUARD_ENABLED requiere SPRING_PROFILES_ACTIVE=prod");
        }

        require(!bootstrapEnabled, "BOOTSTRAP_ENABLED debe permanecer false en producción");
        require(xsdEnabled, "SUNAT_XSD_ENABLED debe ser true en producción");
        require(xsdFailFast, "app.sunat.xsd.fail-fast debe ser true en producción");
        require(!apiDocsEnabled && !swaggerEnabled, "OpenAPI/Swagger debe estar deshabilitado en producción");
        require("database".equalsIgnoreCase(artifactBackend),
                "ARTIFACTS_BACKEND debe ser database en la imagen productiva");
        require(environment.getProperty("app.artifacts.require-checksum", Boolean.class, false),
                "ARTIFACTS_REQUIRE_CHECKSUM debe ser true en producción");
        require(isSecurePublicUrl(publicBaseUrl),
                "PUBLIC_BASE_URL debe ser una URL HTTPS absoluta sin userinfo en producción");

        String datasourceUrl = environment.getProperty("spring.datasource.url", "");
        String datasourceUsername = environment.getProperty("spring.datasource.username", "");
        String datasourcePassword = environment.getProperty("spring.datasource.password", "");
        require(datasourceUrl.startsWith("jdbc:postgresql://"),
                "DATABASE_URL debe ser una URL JDBC PostgreSQL explícita en producción");
        require(!datasourceUsername.isBlank(), "DATABASE_USERNAME es obligatorio en producción");
        require(isNonDefaultPassword(datasourcePassword),
                "DATABASE_PASSWORD debe estar configurado y no puede usar un valor de ejemplo");

        String corsOrigins = environment.getProperty("app.security.cors.allowed-origins", "");
        require(secureCorsOrigins(corsOrigins),
                "CORS_ALLOWED_ORIGINS solo puede contener orígenes HTTPS absolutos en producción");

        String productionUrl = environment.getProperty("app.sunat.production-url", "");
        String consultUrl = environment.getProperty("app.sunat.consult-url", "");
        require(isOfficialSunatHttpsUrl(productionUrl),
                "SUNAT_PRODUCTION_URL debe apuntar por HTTPS a un host oficial sunat.gob.pe");
        require(isOfficialSunatHttpsUrl(consultUrl),
                "SUNAT_CONSULT_URL debe apuntar por HTTPS a un host oficial sunat.gob.pe");
    }

    private static boolean isSecurePublicUrl(String value) {
        try {
            URI uri = URI.create(value);
            return "https".equalsIgnoreCase(uri.getScheme())
                    && uri.getHost() != null
                    && !uri.getHost().isBlank()
                    && uri.getUserInfo() == null
                    && uri.getQuery() == null
                    && uri.getFragment() == null;
        } catch (RuntimeException exception) {
            return false;
        }
    }

    private static boolean secureCorsOrigins(String value) {
        if (value == null || value.isBlank()) {
            return true;
        }
        return Arrays.stream(value.split(","))
                .map(String::trim)
                .filter(origin -> !origin.isBlank())
                .allMatch(ProductionConfigurationGuard::isSecureOrigin);
    }

    private static boolean isSecureOrigin(String value) {
        try {
            URI uri = URI.create(value);
            String path = uri.getPath();
            return "https".equalsIgnoreCase(uri.getScheme())
                    && uri.getHost() != null
                    && !uri.getHost().isBlank()
                    && uri.getUserInfo() == null
                    && (path == null || path.isBlank() || "/".equals(path))
                    && uri.getQuery() == null
                    && uri.getFragment() == null;
        } catch (RuntimeException exception) {
            return false;
        }
    }

    private static boolean isOfficialSunatHttpsUrl(String value) {
        try {
            URI uri = URI.create(value);
            String host = uri.getHost();
            return "https".equalsIgnoreCase(uri.getScheme())
                    && host != null
                    && ("sunat.gob.pe".equalsIgnoreCase(host)
                            || host.toLowerCase(java.util.Locale.ROOT).endsWith(".sunat.gob.pe"))
                    && uri.getUserInfo() == null;
        } catch (RuntimeException exception) {
            return false;
        }
    }

    private static boolean isNonDefaultPassword(String value) {
        if (value == null || value.isBlank()) {
            return false;
        }
        String normalized = value.trim().toLowerCase(java.util.Locale.ROOT);
        return !normalized.equals("perubilling")
                && !normalized.equals("change_me")
                && !normalized.equals("changeme")
                && !normalized.equals("password");
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException(message);
        }
    }
}
