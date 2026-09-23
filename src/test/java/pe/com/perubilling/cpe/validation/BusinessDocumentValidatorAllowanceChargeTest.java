package pe.com.perubilling.cpe.validation;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import pe.com.perubilling.billing.domain.AllowanceChargeEntity;
import pe.com.perubilling.billing.domain.ElectronicDocumentEntity;
import pe.com.perubilling.billing.domain.ElectronicDocumentItemEntity;
import pe.com.perubilling.cpe.domain.DocumentBundle;
import pe.com.perubilling.issuer.domain.IssuerEntity;
import pe.com.perubilling.shared.domain.BusinessException;
import pe.com.perubilling.shared.domain.DocumentType;

class BusinessDocumentValidatorAllowanceChargeTest {
    private final BusinessDocumentValidator validator = new BusinessDocumentValidator();

    @Test
    void acceptsConsistentLineDiscount() {
        UUID itemId = UUID.randomUUID();
        var bundle = new DocumentBundle(
                document(), issuer(), List.of(item(itemId)), List.of(), List.of(discount(itemId, "10.00")));

        assertDoesNotThrow(() -> validator.validate(bundle));
    }

    @Test
    void rejectsTamperedAdjustmentAmount() {
        UUID itemId = UUID.randomUUID();
        var bundle = new DocumentBundle(
                document(), issuer(), List.of(item(itemId)), List.of(), List.of(discount(itemId, "9.99")));

        assertThrows(BusinessException.class, () -> validator.validate(bundle));
    }

    private ElectronicDocumentEntity document() {
        var d = new ElectronicDocumentEntity();
        d.setDocumentType(DocumentType.RECEIPT);
        d.setSeries("B001");
        d.setCorrelativo(1);
        d.setFullNumber("B001-1");
        d.setIssueDate(LocalDate.of(2026, 9, 12));
        d.setCustomerDocumentType("1");
        d.setCustomerDocumentNumber("12345678");
        d.setTaxableAmount(new BigDecimal("90.00"));
        d.setIvapTaxableAmount(BigDecimal.ZERO);
        d.setExoneratedAmount(BigDecimal.ZERO);
        d.setUnaffectedAmount(BigDecimal.ZERO);
        d.setExportAmount(BigDecimal.ZERO);
        d.setFreeAmount(BigDecimal.ZERO);
        d.setIgvAmount(new BigDecimal("16.20"));
        d.setIvapAmount(BigDecimal.ZERO);
        d.setFreeTaxAmount(BigDecimal.ZERO);
        d.setIcbperAmount(BigDecimal.ZERO);
        d.setAllowanceTotalAmount(new BigDecimal("10.00"));
        d.setChargeTotalAmount(BigDecimal.ZERO);
        d.setTotalAmount(new BigDecimal("106.20"));
        return d;
    }

    private ElectronicDocumentItemEntity item(UUID id) {
        var item = new ElectronicDocumentItemEntity();
        item.setId(id);
        item.setLineNumber(1);
        item.setQuantity(BigDecimal.ONE);
        item.setUnitValue(new BigDecimal("100.000000"));
        item.setUnitPrice(new BigDecimal("106.200000"));
        item.setTaxAffectationCode("10");
        item.setIgvRate(new BigDecimal("18.00"));
        item.setLineGrossAmount(new BigDecimal("100.00"));
        item.setLineAllowanceAmount(new BigDecimal("10.00"));
        item.setLineChargeAmount(BigDecimal.ZERO);
        item.setLineBaseAmount(new BigDecimal("90.00"));
        item.setLineIgvAmount(new BigDecimal("16.20"));
        item.setLineIcbperAmount(BigDecimal.ZERO);
        item.setLineTotalAmount(new BigDecimal("106.20"));
        item.setFreeOperation(false);
        return item;
    }

    private AllowanceChargeEntity discount(UUID itemId, String amount) {
        var adjustment = new AllowanceChargeEntity();
        adjustment.setDocumentItemId(itemId);
        adjustment.setLineNumber(1);
        adjustment.setSequenceNumber(1);
        adjustment.setCharge(false);
        adjustment.setReasonCode("00");
        adjustment.setFactor(new BigDecimal("0.100000"));
        adjustment.setAmount(new BigDecimal(amount));
        adjustment.setBaseAmount(new BigDecimal("100.00"));
        return adjustment;
    }

    private IssuerEntity issuer() {
        var issuer = new IssuerEntity();
        issuer.setRuc("20123456786");
        return issuer;
    }
}
