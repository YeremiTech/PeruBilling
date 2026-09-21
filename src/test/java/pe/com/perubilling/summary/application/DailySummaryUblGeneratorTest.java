package pe.com.perubilling.summary.application;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import pe.com.perubilling.billing.domain.ElectronicDocumentEntity;
import pe.com.perubilling.issuer.domain.IssuerEntity;
import pe.com.perubilling.shared.domain.DocumentType;
import pe.com.perubilling.summary.domain.DailySummaryEntity;
import pe.com.perubilling.summary.domain.DailySummaryStatus;

class DailySummaryUblGeneratorTest {
    private final DailySummaryUblGenerator generator = new DailySummaryUblGenerator();

    @Test
    void anonymousCustomerUsesDashForBothSunatIdentifiers() {
        var receipt = baseDocument(DocumentType.RECEIPT);
        receipt.setCustomerDocumentType("0");
        receipt.setCustomerDocumentNumber("-");

        String xml = generate(receipt);

        assertTrue(xml.contains("<cbc:CustomerAssignedAccountID>-</cbc:CustomerAssignedAccountID>"));
        assertTrue(xml.contains("<cbc:AdditionalAccountID>-</cbc:AdditionalAccountID>"));
        assertFalse(xml.contains("<cbc:AdditionalAccountID>0</cbc:AdditionalAccountID>"));
    }

    @Test
    void mandatoryBillingPaymentsAreEmittedEvenWhenZero() {
        var receipt = baseDocument(DocumentType.RECEIPT);
        receipt.setTaxableAmount(new BigDecimal("100.00"));
        receipt.setIgvAmount(new BigDecimal("18.00"));
        receipt.setTotalAmount(new BigDecimal("118.00"));

        String xml = generate(receipt);

        assertContainsPayment(xml, "01", "100.00");
        assertContainsPayment(xml, "02", "0.00");
        assertContainsPayment(xml, "03", "0.00");
    }

    @Test
    void exoneratedReceiptKeepsMandatoryZeroPayments() {
        var receipt = baseDocument(DocumentType.RECEIPT);
        receipt.setExoneratedAmount(new BigDecimal("80.00"));
        receipt.setTotalAmount(new BigDecimal("80.00"));

        String xml = generate(receipt);

        assertContainsPayment(xml, "01", "0.00");
        assertContainsPayment(xml, "02", "80.00");
        assertContainsPayment(xml, "03", "0.00");
    }

    @Test
    void unaffectedReceiptKeepsMandatoryZeroPayments() {
        var receipt = baseDocument(DocumentType.RECEIPT);
        receipt.setUnaffectedAmount(new BigDecimal("55.00"));
        receipt.setTotalAmount(new BigDecimal("55.00"));

        String xml = generate(receipt);

        assertContainsPayment(xml, "01", "0.00");
        assertContainsPayment(xml, "02", "0.00");
        assertContainsPayment(xml, "03", "55.00");
    }

    @Test
    void freeTaxedOperationUsesReferenceAmountButDoesNotPayReferentialIgv() {
        var receipt = baseDocument(DocumentType.RECEIPT);
        receipt.setFreeAmount(new BigDecimal("250.00"));
        receipt.setFreeTaxAmount(new BigDecimal("45.00"));
        receipt.setTotalAmount(BigDecimal.ZERO);

        String xml = generate(receipt);

        assertContainsPayment(xml, "01", "0.00");
        assertContainsPayment(xml, "02", "0.00");
        assertContainsPayment(xml, "03", "0.00");
        assertContainsPayment(xml, "05", "250.00");
        assertTrue(xml.contains("<cbc:TaxAmount currencyID=\"PEN\">0.00</cbc:TaxAmount>"));
        assertTrue(xml.contains("<cbc:ID>1000</cbc:ID>"));
        assertTrue(xml.contains("<cbc:Name>IGV</cbc:Name>"));
        assertFalse(xml.contains("<cbc:ID>9996</cbc:ID>"));
        assertFalse(xml.contains("<cbc:Name>GRA</cbc:Name>"));
        assertFalse(xml.contains(">45.00</cbc:TaxAmount>"));
    }

    @Test
    void creditNoteIncludesReferenceToReceipt() {
        var creditNote = baseDocument(DocumentType.CREDIT_NOTE);
        creditNote.setFullNumber("BC01-1");
        creditNote.setReferenceDocumentType("03");
        creditNote.setReferenceDocumentNumber("B001-99");

        String xml = generate(creditNote);

        assertTrue(xml.contains("<cbc:DocumentTypeCode>07</cbc:DocumentTypeCode>"));
        assertTrue(xml.contains("<cbc:ID>B001-99</cbc:ID>"));
        assertTrue(xml.contains("<cbc:DocumentTypeCode>03</cbc:DocumentTypeCode>"));
    }

    @Test
    void debitNoteIncludesReferenceToReceipt() {
        var debitNote = baseDocument(DocumentType.DEBIT_NOTE);
        debitNote.setFullNumber("BD01-1");
        debitNote.setReferenceDocumentType("03");
        debitNote.setReferenceDocumentNumber("B001-100");

        String xml = generate(debitNote);

        assertTrue(xml.contains("<cbc:DocumentTypeCode>08</cbc:DocumentTypeCode>"));
        assertTrue(xml.contains("<cbc:ID>B001-100</cbc:ID>"));
        assertTrue(xml.contains("<cbc:DocumentTypeCode>03</cbc:DocumentTypeCode>"));
    }

    private String generate(ElectronicDocumentEntity document) {
        return new String(generator.generate(
                summary(), issuer(), List.of(document), LocalDate.of(2026, 9, 12)),
                StandardCharsets.UTF_8);
    }

    private void assertContainsPayment(String xml, String instructionId, String amount) {
        String marker = "<cbc:PaidAmount currencyID=\"PEN\">" + amount
                + "</cbc:PaidAmount><cbc:InstructionID>" + instructionId + "</cbc:InstructionID>";
        assertTrue(xml.contains(marker),
                () -> "No se encontró BillingPayment " + instructionId + "=" + amount + "\n" + xml);
    }

    private ElectronicDocumentEntity baseDocument(DocumentType type) {
        var document = new ElectronicDocumentEntity();
        document.setDocumentType(type);
        document.setSeries(type == DocumentType.RECEIPT ? "B001" : "BC01");
        document.setCorrelativo(1);
        document.setFullNumber(document.getSeries() + "-1");
        document.setIssueDate(LocalDate.of(2026, 9, 11));
        document.setIssueTime(LocalTime.of(12, 0));
        document.setOperationType("0101");
        document.setCurrency("PEN");
        document.setCustomerDocumentType("1");
        document.setCustomerDocumentNumber("12345678");
        document.setCustomerName("CLIENTE");
        document.setTaxableAmount(BigDecimal.ZERO);
        document.setIvapTaxableAmount(BigDecimal.ZERO);
        document.setExoneratedAmount(BigDecimal.ZERO);
        document.setUnaffectedAmount(BigDecimal.ZERO);
        document.setExportAmount(BigDecimal.ZERO);
        document.setFreeAmount(BigDecimal.ZERO);
        document.setIgvAmount(BigDecimal.ZERO);
        document.setIvapAmount(BigDecimal.ZERO);
        document.setFreeTaxAmount(BigDecimal.ZERO);
        document.setIcbperAmount(BigDecimal.ZERO);
        document.setTotalAmount(BigDecimal.ZERO);
        document.setSummaryConditionCode("1");
        return document;
    }

    private DailySummaryEntity summary() {
        var summary = new DailySummaryEntity();
        summary.setIdentifier("RC-20260911-1");
        summary.setReferenceDate(LocalDate.of(2026, 9, 11));
        summary.setSequenceNumber(1);
        summary.setStatus(DailySummaryStatus.QUEUED);
        return summary;
    }

    private IssuerEntity issuer() {
        var issuer = new IssuerEntity();
        issuer.setRuc("20123456786");
        issuer.setBusinessName("EMISOR SAC");
        issuer.setAddress("Lima");
        issuer.setUbigeo("150101");
        return issuer;
    }
}
