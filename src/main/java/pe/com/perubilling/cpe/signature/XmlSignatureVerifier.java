package pe.com.perubilling.cpe.signature;

import java.security.Key;
import java.security.PublicKey;
import java.security.cert.X509Certificate;
import java.util.List;
import javax.xml.crypto.AlgorithmMethod;
import javax.xml.crypto.KeySelector;
import javax.xml.crypto.KeySelectorException;
import javax.xml.crypto.KeySelectorResult;
import javax.xml.crypto.XMLCryptoContext;
import javax.xml.crypto.XMLStructure;
import javax.xml.crypto.dsig.XMLSignature;
import javax.xml.crypto.dsig.XMLSignatureFactory;
import javax.xml.crypto.dsig.dom.DOMValidateContext;
import javax.xml.crypto.dsig.keyinfo.KeyInfo;
import javax.xml.crypto.dsig.keyinfo.KeyValue;
import javax.xml.crypto.dsig.keyinfo.X509Data;
import org.springframework.stereotype.Component;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import pe.com.perubilling.shared.xml.SecureXml;

/** Verifica integridad criptográfica XMLDSig; no sustituye validación de confianza/PKI de SUNAT. */
@Component
public class XmlSignatureVerifier {
    public VerificationResult verify(byte[] signedXml) {
        try {
            Document document = SecureXml.parse(signedXml);
            NodeList signatures = document.getElementsByTagNameNS(XMLSignature.XMLNS, "Signature");
            if (signatures.getLength() != 1) {
                return new VerificationResult(false, "Se esperaba exactamente una firma XMLDSig");
            }
            Node signatureNode = signatures.item(0);
            CertificateOrKeySelector selector = new CertificateOrKeySelector();
            DOMValidateContext context = new DOMValidateContext(selector, signatureNode);
            context.setProperty("org.jcp.xml.dsig.secureValidation", Boolean.TRUE);
            XMLSignature signature = XMLSignatureFactory.getInstance("DOM").unmarshalXMLSignature(context);
            boolean valid = signature.validate(context);
            if (!valid) return new VerificationResult(false, "La firma XMLDSig no es válida");
            if (selector.certificate != null) selector.certificate.checkValidity();
            return new VerificationResult(true, "OK");
        } catch (Exception ex) {
            return new VerificationResult(false, ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage());
        }
    }

    public void requireValid(byte[] signedXml) {
        VerificationResult result = verify(signedXml);
        if (!result.valid()) throw new IllegalStateException("XMLDSig inválida: " + result.message());
    }

    public record VerificationResult(boolean valid, String message) {}

    private static final class CertificateOrKeySelector extends KeySelector {
        private X509Certificate certificate;

        @Override
        public KeySelectorResult select(KeyInfo keyInfo, Purpose purpose, AlgorithmMethod method, XMLCryptoContext context)
                throws KeySelectorException {
            if (keyInfo == null) throw new KeySelectorException("XMLDSig no contiene KeyInfo");
            for (Object entry : keyInfo.getContent()) {
                XMLStructure structure = (XMLStructure) entry;
                if (structure instanceof X509Data data) {
                    for (Object value : data.getContent()) {
                        if (value instanceof X509Certificate cert) {
                            certificate = cert;
                            PublicKey key = cert.getPublicKey();
                            return () -> key;
                        }
                    }
                }
                if (structure instanceof KeyValue keyValue) {
                    try {
                        PublicKey key = keyValue.getPublicKey();
                        return () -> key;
                    } catch (Exception ex) {
                        throw new KeySelectorException(ex);
                    }
                }
            }
            throw new KeySelectorException("No se encontró certificado ni clave pública en KeyInfo");
        }
    }
}
