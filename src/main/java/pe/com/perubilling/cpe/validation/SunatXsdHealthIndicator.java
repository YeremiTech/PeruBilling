package pe.com.perubilling.cpe.validation;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.stereotype.Component;

@Component("sunatXsd")
public class SunatXsdHealthIndicator implements HealthIndicator {
    private final boolean enabled;
    private final Path root;

    public SunatXsdHealthIndicator(@Value("${app.sunat.xsd.enabled:false}") boolean enabled,
                                   @Value("${app.sunat.xsd.path:./sunat-resources/xsd}") String path) {
        this.enabled = enabled;
        this.root = Path.of(path).toAbsolutePath().normalize();
    }

    @Override
    public Health health() {
        if (!enabled) {
            return Health.unknown().withDetail("enabled", false)
                    .withDetail("message", "Validación XSD deshabilitada para este perfil").build();
        }
        List<String> required = List.of(
                "UBL-Invoice-2.1.xsd",
                "UBL-CreditNote-2.1.xsd",
                "UBL-DebitNote-2.1.xsd",
                "SummaryDocuments-1.xsd",
                "VoidedDocuments-1.xsd");
        List<String> missing = new ArrayList<>();
        for (String file : required) {
            if (!existsRecursively(file)) missing.add(file);
        }
        if (!missing.isEmpty()) {
            return Health.down().withDetail("enabled", true).withDetail("root", root.toString())
                    .withDetail("missing", missing).build();
        }
        return Health.up().withDetail("enabled", true).withDetail("root", root.toString())
                .withDetail("requiredSchemas", required.size()).build();
    }

    private boolean existsRecursively(String fileName) {
        if (!Files.isDirectory(root)) return false;
        try (var stream = Files.walk(root)) {
            return stream.filter(Files::isRegularFile)
                    .anyMatch(p -> p.getFileName().toString().equals(fileName));
        } catch (Exception ex) {
            return false;
        }
    }
}
