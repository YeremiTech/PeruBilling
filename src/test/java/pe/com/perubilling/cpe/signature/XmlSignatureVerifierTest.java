package pe.com.perubilling.cpe.signature;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.security.KeyPairGenerator;
import java.util.List;
import javax.xml.crypto.dsig.CanonicalizationMethod;
import javax.xml.crypto.dsig.DigestMethod;
import javax.xml.crypto.dsig.SignatureMethod;
import javax.xml.crypto.dsig.Transform;
import javax.xml.crypto.dsig.XMLSignatureFactory;
import javax.xml.crypto.dsig.dom.DOMSignContext;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import org.junit.jupiter.api.Test;
import pe.com.perubilling.shared.xml.SecureXml;

class XmlSignatureVerifierTest {
    private final XmlSignatureVerifier verifier = new XmlSignatureVerifier();

    @Test
    void acceptsValidSignatureAndRejectsTampering() throws Exception {
        byte[] signed = sign("<Invoice><Amount>118.00</Amount></Invoice>");
        assertTrue(verifier.verify(signed).valid());

        String changed = new String(signed, java.nio.charset.StandardCharsets.UTF_8)
                .replace("118.00", "119.00");
        assertFalse(verifier.verify(changed.getBytes(java.nio.charset.StandardCharsets.UTF_8)).valid());
    }

    private byte[] sign(String xml) throws Exception {
        var pairGenerator = KeyPairGenerator.getInstance("RSA");
        pairGenerator.initialize(2048);
        var pair = pairGenerator.generateKeyPair();
        var document = SecureXml.parse(xml.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        var factory = XMLSignatureFactory.getInstance("DOM");
        var reference = factory.newReference("",
                factory.newDigestMethod(DigestMethod.SHA256, null),
                List.of(factory.newTransform(Transform.ENVELOPED,
                        (javax.xml.crypto.dsig.spec.TransformParameterSpec) null)), null, null);
        var signedInfo = factory.newSignedInfo(
                factory.newCanonicalizationMethod(CanonicalizationMethod.INCLUSIVE,
                        (javax.xml.crypto.dsig.spec.C14NMethodParameterSpec) null),
                factory.newSignatureMethod(SignatureMethod.RSA_SHA256, null),
                List.of(reference));
        var keyInfoFactory = factory.getKeyInfoFactory();
        var keyValue = keyInfoFactory.newKeyValue(pair.getPublic());
        var keyInfo = keyInfoFactory.newKeyInfo(List.of(keyValue));
        var context = new DOMSignContext(pair.getPrivate(), document.getDocumentElement());
        factory.newXMLSignature(signedInfo, keyInfo).sign(context);

        var tf = TransformerFactory.newInstance();
        var transformer = tf.newTransformer();
        transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "no");
        var out = new ByteArrayOutputStream();
        transformer.transform(new DOMSource(document), new StreamResult(out));
        return out.toByteArray();
    }
}
