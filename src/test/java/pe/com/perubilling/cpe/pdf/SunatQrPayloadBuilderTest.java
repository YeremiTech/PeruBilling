package pe.com.perubilling.cpe.pdf;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import pe.com.perubilling.billing.domain.ElectronicDocumentEntity;
import pe.com.perubilling.cpe.domain.DocumentBundle;
import pe.com.perubilling.issuer.domain.IssuerEntity;
import pe.com.perubilling.shared.domain.DocumentType;

class SunatQrPayloadBuilderTest {
    private final SunatQrPayloadBuilder builder = new SunatQrPayloadBuilder(new XmlDigestValueExtractor());

    @Test
    void includesDigestFromSignedXml() {
        var issuer = issuer();
        var document = base(DocumentType.INVOICE, "F001", 25, "6", "20123456789", new BigDecimal("118.00"));
        byte[] xml = ("<Invoice xmlns:ds=\"http://www.w3.org/2000/09/xmldsig#\">"
                + "<ds:DigestValue>ABC123==</ds:DigestValue></Invoice>").getBytes(java.nio.charset.StandardCharsets.UTF_8);

        String payload = builder.build(new DocumentBundle(document, issuer, List.of(), List.of()), xml);

        assertThat(payload).isEqualTo("20100066603|01|F001|25|18.00|118.00|2026-09-12|6|20123456789|ABC123==|");
    }

    @Test
    void anonymousReceiptLeavesCustomerFieldsEmpty() {
        var issuer = issuer();
        var document = base(DocumentType.RECEIPT, "B001", 9, "0", "-", new BigDecimal("50.00"));
        document.setIgvAmount(new BigDecimal("7.63"));
        byte[] xml = "<Invoice/>".getBytes(java.nio.charset.StandardCharsets.UTF_8);

        String payload = builder.build(new DocumentBundle(document, issuer, List.of(), List.of()), xml);

        assertThat(payload).contains("|03|B001|9|7.63|50.00|2026-09-12|||" );
    }

    private IssuerEntity issuer() {
        var issuer = new IssuerEntity();
        issuer.setRuc("20100066603");
        return issuer;
    }

    private ElectronicDocumentEntity base(DocumentType type, String series, long number,
                                           String customerType, String customerNumber, BigDecimal total) {
        var d = new ElectronicDocumentEntity();
        d.setDocumentType(type);
        d.setSeries(series);
        d.setCorrelativo(number);
        d.setFullNumber(series + "-" + number);
        d.setIssueDate(LocalDate.of(2026, 9, 12));
        d.setCurrency("PEN");
        d.setCustomerDocumentType(customerType);
        d.setCustomerDocumentNumber(customerNumber);
        d.setCustomerName("CLIENTE");
        d.setIgvAmount(new BigDecimal("18.00"));
        d.setTotalAmount(total);
        return d;
    }
}
