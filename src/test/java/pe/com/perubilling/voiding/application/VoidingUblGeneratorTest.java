package pe.com.perubilling.voiding.application;

import static org.junit.jupiter.api.Assertions.assertTrue;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import pe.com.perubilling.shared.domain.DocumentType;
import pe.com.perubilling.billing.domain.ElectronicDocumentEntity;
import pe.com.perubilling.issuer.domain.IssuerEntity;
import pe.com.perubilling.voiding.domain.VoidingBatchEntity;

class VoidingUblGeneratorTest {
    @Test
    void generatesRaWithDocumentIdentityAndReason() {
        var issuer = new IssuerEntity();
        issuer.setRuc("20123456786");
        issuer.setBusinessName("EMISOR SAC");

        var doc = new ElectronicDocumentEntity();
        doc.setDocumentType(DocumentType.INVOICE);
        doc.setSeries("F001");
        doc.setCorrelativo(125);
        doc.setIssueDate(LocalDate.of(2026,9,12));

        var batch = new VoidingBatchEntity();
        batch.setIdentifier("RA-20260912-1");
        batch.setReferenceDate(LocalDate.of(2026,9,12));
        batch.setGenerationDate(LocalDate.of(2026,9,12));
        batch.setReason("Documento no otorgado");

        String xml = new String(new VoidingUblGenerator().generate(batch,issuer,doc),
                java.nio.charset.StandardCharsets.UTF_8);
        assertTrue(xml.contains("<cbc:ID>RA-20260912-1</cbc:ID>"));
        assertTrue(xml.contains("<cbc:DocumentTypeCode>01</cbc:DocumentTypeCode>"));
        assertTrue(xml.contains("<cbc:DocumentSerialID>F001</cbc:DocumentSerialID>"));
        assertTrue(xml.contains("<cbc:DocumentNumberID>125</cbc:DocumentNumberID>"));
        assertTrue(xml.contains("Documento no otorgado"));
    }
}
