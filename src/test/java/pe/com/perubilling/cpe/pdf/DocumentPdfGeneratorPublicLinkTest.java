package pe.com.perubilling.cpe.pdf;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;
import pe.com.perubilling.billing.domain.ElectronicDocumentEntity;
import pe.com.perubilling.billing.domain.ElectronicDocumentItemEntity;
import pe.com.perubilling.cpe.domain.DocumentBundle;
import pe.com.perubilling.issuer.domain.IssuerEntity;
import pe.com.perubilling.shared.domain.DocumentType;

class DocumentPdfGeneratorPublicLinkTest {
    @Test
    void printedRepresentationContainsCustomerConsultationUrl() throws Exception {
        var generator = new DocumentPdfGenerator(
                new SunatQrPayloadBuilder(new XmlDigestValueExtractor()), new SunatQrCodeGenerator());
        String url = "https://billing.example.com/public/v1/documents/abc123XYZ987";
        byte[] pdf = generator.generate(bundle(), "<root/>".getBytes(), url);
        try (var document = Loader.loadPDF(pdf)) {
            String text = new PDFTextStripper().getText(document);
            assertTrue(text.contains("Consulta y descarga del comprobante"));
            assertTrue(text.contains("https://billing.example.com/public/v1/documents/abc123XYZ987"));
        }
    }

    private DocumentBundle bundle() {
        var d = new ElectronicDocumentEntity();
        d.setDocumentType(DocumentType.INVOICE); d.setSeries("F001"); d.setCorrelativo(1); d.setFullNumber("F001-1");
        d.setIssueDate(LocalDate.of(2026,9,12)); d.setIssueTime(LocalTime.of(10,0)); d.setCurrency("PEN");
        d.setCustomerDocumentType("6"); d.setCustomerDocumentNumber("20123456786"); d.setCustomerName("CLIENTE SAC");
        d.setTaxableAmount(new BigDecimal("100.00")); d.setIgvAmount(new BigDecimal("18.00")); d.setTotalAmount(new BigDecimal("118.00"));
        var i = new ElectronicDocumentItemEntity(); i.setLineNumber(1); i.setDescription("Producto"); i.setUnitCode("NIU");
        i.setQuantity(BigDecimal.ONE); i.setUnitValue(new BigDecimal("100.000000")); i.setLineTotalAmount(new BigDecimal("118.00"));
        var issuer = new IssuerEntity(); issuer.setRuc("20123456786"); issuer.setBusinessName("EMISOR SAC"); issuer.setAddress("Lima"); issuer.setUbigeo("150101");
        return new DocumentBundle(d, issuer, List.of(i), List.of());
    }
}
