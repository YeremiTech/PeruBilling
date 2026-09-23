package pe.com.perubilling.cpe.signature;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.util.List;
import javax.xml.XMLConstants;
import javax.xml.crypto.dsig.CanonicalizationMethod;
import javax.xml.crypto.dsig.DigestMethod;
import javax.xml.crypto.dsig.Reference;
import javax.xml.crypto.dsig.SignatureMethod;
import javax.xml.crypto.dsig.SignedInfo;
import javax.xml.crypto.dsig.Transform;
import javax.xml.crypto.dsig.XMLSignatureFactory;
import javax.xml.crypto.dsig.dom.DOMSignContext;
import javax.xml.crypto.dsig.keyinfo.KeyInfo;
import javax.xml.crypto.dsig.keyinfo.KeyInfoFactory;
import javax.xml.crypto.dsig.keyinfo.X509Data;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import org.springframework.stereotype.Component;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import pe.com.perubilling.issuer.domain.DigitalCertificateEntity;
import pe.com.perubilling.shared.crypto.SecretCryptoService;
import pe.com.perubilling.shared.xml.SecureXml;

@Component
public class Pkcs12XmlDocumentSigner implements XmlDocumentSigner {
    private static final String EXTENSION_NS = "urn:oasis:names:specification:ubl:schema:xsd:CommonExtensionComponents-2";
    private final SecretCryptoService crypto;

    public Pkcs12XmlDocumentSigner(SecretCryptoService crypto) { this.crypto = crypto; }

    @Override
    public byte[] sign(byte[] xmlBytes, DigitalCertificateEntity certificateEntity) {
        try {
            byte[] pfx = crypto.decryptBytes(certificateEntity.getEncryptedPfx());
            char[] password = crypto.decrypt(certificateEntity.getPasswordEncrypted()).toCharArray();
            KeyStore store = KeyStore.getInstance("PKCS12");
            store.load(new ByteArrayInputStream(pfx), password);
            String alias = certificateEntity.getCertificateAlias();
            PrivateKey privateKey = (PrivateKey) store.getKey(alias, password);
            X509Certificate certificate = (X509Certificate) store.getCertificate(alias);
            if (privateKey == null || certificate == null) throw new IllegalStateException("El certificado no contiene clave privada utilizable");
            certificate.checkValidity();

            Document document = SecureXml.parse(xmlBytes);
            NodeList nodes = document.getElementsByTagNameNS(EXTENSION_NS, "ExtensionContent");
            if (nodes.getLength() == 0) throw new IllegalStateException("El UBL no contiene ext:ExtensionContent para la firma");
            Node extensionContent = nodes.item(0);

            XMLSignatureFactory factory = XMLSignatureFactory.getInstance("DOM");
            Reference reference = factory.newReference("",
                    factory.newDigestMethod(DigestMethod.SHA256, null),
                    List.of(factory.newTransform(Transform.ENVELOPED, (javax.xml.crypto.dsig.spec.TransformParameterSpec) null),
                            factory.newTransform(CanonicalizationMethod.INCLUSIVE, (javax.xml.crypto.dsig.spec.TransformParameterSpec) null)),
                    null, null);
            SignedInfo signedInfo = factory.newSignedInfo(
                    factory.newCanonicalizationMethod(CanonicalizationMethod.INCLUSIVE, (javax.xml.crypto.dsig.spec.C14NMethodParameterSpec) null),
                    factory.newSignatureMethod(SignatureMethod.RSA_SHA256, null),
                    List.of(reference));
            KeyInfoFactory keyInfoFactory = factory.getKeyInfoFactory();
            X509Data x509Data = keyInfoFactory.newX509Data(List.of(certificate));
            KeyInfo keyInfo = keyInfoFactory.newKeyInfo(List.of(x509Data));

            DOMSignContext signContext = new DOMSignContext(privateKey, extensionContent);
            signContext.setDefaultNamespacePrefix("ds");
            var signature = factory.newXMLSignature(signedInfo, keyInfo, null, "SignatureSP", null);
            signature.sign(signContext);

            TransformerFactory tf = TransformerFactory.newInstance();
            tf.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            tf.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            tf.setAttribute(XMLConstants.ACCESS_EXTERNAL_STYLESHEET, "");
            var transformer = tf.newTransformer();
            transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
            transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "no");
            transformer.setOutputProperty(OutputKeys.INDENT, "no");
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            transformer.transform(new DOMSource(document), new StreamResult(output));
            return output.toByteArray();
        } catch (Exception ex) {
            throw new IllegalStateException("No se pudo firmar digitalmente el XML", ex);
        }
    }
}
