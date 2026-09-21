package pe.com.perubilling.sunat.infrastructure;

import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.w3c.dom.Document;
import org.w3c.dom.NodeList;
import pe.com.perubilling.shared.xml.SecureXml;
import pe.com.perubilling.sunat.domain.SunatSubmissionResult;

@Component
public class CdrParser {
    private final ZipSupport.Limits limits;

    public CdrParser(
            @Value("${app.sunat.cdr.max-zip-bytes:5242880}") long maxZipBytes,
            @Value("${app.sunat.cdr.max-xml-bytes:10485760}") long maxXmlBytes,
            @Value("${app.sunat.cdr.max-entries:8}") int maxEntries,
            @Value("${app.sunat.cdr.max-compression-ratio:200}") long maxCompressionRatio) {
        this.limits = new ZipSupport.Limits(
                maxZipBytes, maxXmlBytes, maxEntries, maxCompressionRatio);
    }

    public SunatSubmissionResult parse(byte[] cdrZip, int httpStatus) {
        Document document = SecureXml.parse(ZipSupport.firstXml(cdrZip, limits));
        String code = first(document, "ResponseCode");
        String description = first(document, "Description");
        List<String> notes = all(document, "Note");
        if (code == null || code.isBlank()) {
            code = "UNKNOWN";
        }
        if (description == null || description.isBlank()) {
            description = "SUNAT no devolvió descripción";
        }
        return new SunatSubmissionResult(
                code, description, List.copyOf(notes), cdrZip, httpStatus);
    }

    private String first(Document doc, String localName) {
        NodeList nodes = doc.getElementsByTagNameNS("*", localName);
        return nodes.getLength() == 0 ? null : nodes.item(0).getTextContent().trim();
    }

    private List<String> all(Document doc, String localName) {
        NodeList nodes = doc.getElementsByTagNameNS("*", localName);
        List<String> values = new ArrayList<>();
        for (int i = 0; i < nodes.getLength(); i++) {
            String value = nodes.item(i).getTextContent();
            if (value != null && !value.isBlank()) {
                values.add(value.trim());
            }
        }
        return values;
    }
}
