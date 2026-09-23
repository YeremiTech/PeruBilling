package pe.com.perubilling.cpe.validation;

import jakarta.annotation.PostConstruct;
import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.xml.XMLConstants;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.SchemaFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.xml.sax.SAXException;
import pe.com.perubilling.shared.domain.DocumentType;
import pe.com.perubilling.issuer.domain.SunatEnvironment;
import pe.com.perubilling.shared.xml.SecureXml;

@Component
public class SunatXsdValidator {
    private final boolean enabled;
    private final boolean failFast;
    private final Path root;

    public SunatXsdValidator(@Value("${app.sunat.xsd.enabled:false}") boolean enabled,
                             @Value("${app.sunat.xsd.fail-fast:false}") boolean failFast,
                             @Value("${app.sunat.xsd.path:./sunat-resources/xsd}") String path) {
        this.enabled = enabled;
        this.failFast = failFast;
        this.root = Path.of(path).toAbsolutePath().normalize();
    }

    @PostConstruct
    void verifyAtStartup() {
        if (!enabled || !failFast) return;
        var missing = requiredSchemas().stream().filter(name -> !Files.isRegularFile(findRecursively(name))).toList();
        if (!missing.isEmpty()) {
            throw new IllegalStateException("Faltan XSD SUNAT obligatorios bajo " + root + ": " + String.join(", ", missing));
        }
    }

    public void requireForEnvironment(SunatEnvironment environment) {
        if (environment != null && environment != SunatEnvironment.LOCAL && !enabled) {
            throw new IllegalStateException(
                    "La validación XSD SUNAT es obligatoria para emisores BETA/PRODUCTION");
        }
    }

    public void validate(byte[] xml, DocumentType type) {
        SecureXml.parse(xml);
        if (!enabled) return;
        Path schemaPath = resolveSchema(type);
        if (!Files.isRegularFile(schemaPath)) {
            schemaPath = findRecursively(schemaPath.getFileName().toString());
        }
        if (!Files.isRegularFile(schemaPath)) {
            throw new IllegalStateException("XSD SUNAT requerido y no encontrado bajo " + root + ": " + schemaPath.getFileName());
        }
        validateAgainst(xml, schemaPath);
    }

    public void validateSummary(byte[] xml) {
        SecureXml.parse(xml);
        if (!enabled) return;
        Path schemaPath = findRecursively("SummaryDocuments-1.xsd");
        if (!Files.isRegularFile(schemaPath)) throw new IllegalStateException("XSD SUNAT UBL 2.0 SummaryDocuments-1.xsd no encontrado bajo " + root);
        validateAgainst(xml, schemaPath);
    }

    public void validateVoiding(byte[] xml) {
        SecureXml.parse(xml);
        if (!enabled) return;
        Path schemaPath = findRecursively("VoidedDocuments-1.xsd");
        if (!Files.isRegularFile(schemaPath)) {
            throw new IllegalStateException("XSD SUNAT UBL 2.0 VoidedDocuments-1.xsd no encontrado bajo " + root);
        }
        validateAgainst(xml, schemaPath);
    }

    private Path findRecursively(String fileName) {
        if (!Files.isDirectory(root)) return root.resolve(fileName);
        try (var stream = Files.walk(root)) {
            return stream.filter(Files::isRegularFile).filter(p -> p.getFileName().toString().equals(fileName)).findFirst().orElse(root.resolve(fileName));
        } catch (java.io.IOException ex) {
            throw new IllegalStateException("No se pudo buscar XSD SUNAT en " + root, ex);
        }
    }

    private void validateAgainst(byte[] xml, Path schemaPath) {
        try {
            SchemaFactory factory = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            factory.setProperty(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            factory.setProperty(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "file");
            var schema = factory.newSchema(schemaPath.toFile());
            var validator = schema.newValidator();
            validator.setProperty(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            validator.setProperty(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "file");
            StreamSource source = new StreamSource(new ByteArrayInputStream(xml));
            source.setSystemId(schemaPath.toUri().toString());
            validator.validate(source);
        } catch (SAXException ex) {
            throw new IllegalArgumentException("XML no cumple el XSD UBL/SUNAT: " + ex.getMessage(), ex);
        } catch (Exception ex) {
            throw new IllegalStateException("No se pudo ejecutar la validación XSD", ex);
        }
    }

    private java.util.List<String> requiredSchemas() {
        return java.util.List.of(
                "UBL-Invoice-2.1.xsd",
                "UBL-CreditNote-2.1.xsd",
                "UBL-DebitNote-2.1.xsd",
                "SummaryDocuments-1.xsd",
                "VoidedDocuments-1.xsd");
    }

    private Path resolveSchema(DocumentType type) {
        String file = switch (type) {
            case INVOICE, RECEIPT -> "maindoc/UBL-Invoice-2.1.xsd";
            case CREDIT_NOTE -> "maindoc/UBL-CreditNote-2.1.xsd";
            case DEBIT_NOTE -> "maindoc/UBL-DebitNote-2.1.xsd";
        };
        return root.resolve(file).normalize();
    }
}
