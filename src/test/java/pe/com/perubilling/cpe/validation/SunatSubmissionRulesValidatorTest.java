package pe.com.perubilling.cpe.validation;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import pe.com.perubilling.billing.domain.ElectronicDocumentEntity;
import pe.com.perubilling.billing.domain.ElectronicDocumentItemEntity;
import pe.com.perubilling.billing.domain.PaymentMethod;
import pe.com.perubilling.cpe.domain.DocumentBundle;
import pe.com.perubilling.issuer.domain.IssuerEntity;
import pe.com.perubilling.shared.domain.BusinessException;
import pe.com.perubilling.shared.domain.DocumentType;

class SunatSubmissionRulesValidatorTest {
    private final SunatSubmissionRulesValidator validator = new SunatSubmissionRulesValidator(3, 7, "2026-08-26");

    @Test
    void anonymousReceiptOverThresholdIsRejected() {
        var d = receipt(new BigDecimal("700.01"));
        assertThatThrownBy(() -> validator.validateForSummary(bundle(d)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("S/ 700");
    }

    @Test
    void invoiceRequiresRucCustomer() {
        var d = receipt(new BigDecimal("100.00"));
        d.setDocumentType(DocumentType.INVOICE);
        d.setSeries("F001");
        d.setFullNumber("F001-1");
        d.setPaymentMethod(PaymentMethod.CONTADO);
        assertThatThrownBy(() -> validator.validateForDirectSubmission(bundle(d)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("RUC");
    }

    @Test
    void unsupportedOperationTypeIsRejectedInsteadOfGeneratingPartialUbl() {
        var d = receipt(new BigDecimal("100.00"));
        d.setOperationType("1001");
        assertThatThrownBy(() -> validator.validateForSummary(bundle(d)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("alcance CORE");
    }

    @Test
    void exportInvoiceIsAcceptedWhenProfileAndCustomerAreConsistent() {
        var d = receipt(new BigDecimal("100.00"));
        d.setDocumentType(DocumentType.INVOICE);
        d.setSeries("F001");
        d.setFullNumber("F001-1");
        d.setOperationType("0102");
        d.setCurrency("USD");
        d.setCustomerDocumentType("0");
        d.setCustomerDocumentNumber("TAX-123");
        d.setCustomerName("FOREIGN CUSTOMER");
        d.setCustomerCountryCode("US");
        d.setPaymentMethod(PaymentMethod.CONTADO);
        d.setExportAmount(new BigDecimal("100.00"));

        var issuer = new IssuerEntity();
        issuer.setRuc("20100066603");
        var item = new ElectronicDocumentItemEntity();
        item.setLineNumber(1);
        item.setUnitCode("NIU");
        item.setTaxAffectationCode("40");

        assertDoesNotThrow(() -> validator.validateForDirectSubmission(
                new DocumentBundle(d, issuer, List.of(item), List.of())));
    }

    @Test
    void exportInvoiceRequiresForeignCountry() {
        var d = receipt(new BigDecimal("100.00"));
        d.setDocumentType(DocumentType.INVOICE);
        d.setSeries("F001");
        d.setFullNumber("F001-1");
        d.setOperationType("0102");
        d.setCurrency("USD");
        d.setCustomerDocumentType("0");
        d.setCustomerDocumentNumber("TAX-123");
        d.setCustomerName("FOREIGN CUSTOMER");
        d.setCustomerCountryCode("PE");
        d.setPaymentMethod(PaymentMethod.CONTADO);
        d.setExportAmount(new BigDecimal("100.00"));
        var issuer = new IssuerEntity();
        issuer.setRuc("20100066603");
        var item = new ElectronicDocumentItemEntity();
        item.setLineNumber(1);
        item.setUnitCode("NIU");
        item.setTaxAffectationCode("40");

        assertThatThrownBy(() -> validator.validateForDirectSubmission(
                new DocumentBundle(d, issuer, List.of(item), List.of())))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("distinto de PE");
    }

    @Test
    void lineChargesRemainBlockedForDailySummaryUntilRegulatoryCalibration() {
        var d = receipt(new BigDecimal("110.00"));
        d.setChargeTotalAmount(new BigDecimal("10.00"));
        assertThatThrownBy(() -> validator.validateForSummary(bundle(d)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Resumen Diario");
    }

    @Test
    void ivapRequiresOperation0107() {
        var d = receipt(new BigDecimal("100.00"));
        var issuer = new IssuerEntity();
        issuer.setRuc("20100066603");
        var item = new ElectronicDocumentItemEntity();
        item.setLineNumber(1);
        item.setUnitCode("NIU");
        item.setTaxAffectationCode("17");
        var ivapBundle = new DocumentBundle(d, issuer, List.of(item), List.of());
        assertThatThrownBy(() -> validator.validateForSummary(ivapBundle))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("0107");
    }

    private DocumentBundle bundle(ElectronicDocumentEntity d) {
        var issuer = new IssuerEntity();
        issuer.setRuc("20100066603");
        var item = new ElectronicDocumentItemEntity();
        item.setLineNumber(1);
        item.setUnitCode("NIU");
        item.setTaxAffectationCode("10");
        return new DocumentBundle(d, issuer, List.of(item), List.of());
    }

    private ElectronicDocumentEntity receipt(BigDecimal total) {
        var d = new ElectronicDocumentEntity();
        d.setDocumentType(DocumentType.RECEIPT);
        d.setSeries("B001");
        d.setCorrelativo(1);
        d.setFullNumber("B001-1");
        d.setIssueDate(LocalDate.now(java.time.ZoneId.of("America/Lima")));
        d.setOperationType("0101");
        d.setCurrency("PEN");
        d.setCustomerDocumentType("0");
        d.setCustomerDocumentNumber("-");
        d.setCustomerName("CONSUMIDOR FINAL");
        d.setTotalAmount(total);
        return d;
    }
}
