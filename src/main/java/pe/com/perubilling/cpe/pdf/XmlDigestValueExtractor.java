package pe.com.perubilling.cpe.pdf;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import org.springframework.stereotype.Component;
import org.w3c.dom.NodeList;
import pe.com.perubilling.shared.xml.SecureXml;

@Component
public class XmlDigestValueExtractor {
    private static final String DS = "http://www.w3.org/2000/09/xmldsig#";

    public String extract(byte[] xml) {
        var document = SecureXml.parse(xml);
        NodeList values = document.getElementsByTagNameNS(DS, "DigestValue");
        if (values.getLength() > 0) {
            String value = values.item(0).getTextContent();
            if (value != null && !value.isBlank()) return value.trim();
        }

        try {
            return Base64.getEncoder().encodeToString(MessageDigest.getInstance("SHA-256").digest(xml));
        } catch (Exception ex) {
            throw new IllegalStateException("No se pudo obtener el valor resumen del XML", ex);
        }
    }
}
