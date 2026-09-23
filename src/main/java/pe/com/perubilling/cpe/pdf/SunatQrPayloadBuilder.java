package pe.com.perubilling.cpe.pdf;

import java.math.BigDecimal;
import java.math.RoundingMode;
import org.springframework.stereotype.Component;
import pe.com.perubilling.cpe.domain.DocumentBundle;

@Component
public class SunatQrPayloadBuilder {
    private final XmlDigestValueExtractor digestExtractor;

    public SunatQrPayloadBuilder(XmlDigestValueExtractor digestExtractor) {
        this.digestExtractor = digestExtractor;
    }

    public String build(DocumentBundle bundle, byte[] signedXml) {
        var d = bundle.document();
        return String.join("|",
                bundle.issuer().getRuc(),
                d.getDocumentType().getSunatCode(),
                d.getSeries(),
                Long.toString(d.getCorrelativo()),
                money(d.getIgvAmount()),
                money(d.getTotalAmount()),
                d.getIssueDate().toString(),
                customerType(d),
                customerNumber(d),
                digestExtractor.extract(signedXml),
                "");
    }

    private String money(BigDecimal value) {
        return (value == null ? BigDecimal.ZERO : value).setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    private String customerType(pe.com.perubilling.billing.domain.ElectronicDocumentEntity d) {
        return "0".equals(d.getCustomerDocumentType()) && "-".equals(d.getCustomerDocumentNumber())
                ? "" : value(d.getCustomerDocumentType());
    }

    private String customerNumber(pe.com.perubilling.billing.domain.ElectronicDocumentEntity d) {
        return "0".equals(d.getCustomerDocumentType()) && "-".equals(d.getCustomerDocumentNumber())
                ? "" : value(d.getCustomerDocumentNumber());
    }

    private String value(String value) {
        return value == null ? "" : value;
    }
}
