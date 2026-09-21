package pe.com.perubilling.cpe.signature;

import pe.com.perubilling.issuer.domain.DigitalCertificateEntity;

public interface XmlDocumentSigner {
    byte[] sign(byte[] xml, DigitalCertificateEntity certificate);
}
