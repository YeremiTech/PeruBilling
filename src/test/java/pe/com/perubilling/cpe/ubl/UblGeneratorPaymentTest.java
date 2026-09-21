package pe.com.perubilling.cpe.ubl;

import static org.junit.jupiter.api.Assertions.assertTrue;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import pe.com.perubilling.shared.domain.DocumentType;
import pe.com.perubilling.billing.domain.ElectronicDocumentEntity;
import pe.com.perubilling.billing.domain.ElectronicDocumentItemEntity;
import pe.com.perubilling.billing.domain.PaymentInstallmentEntity;
import pe.com.perubilling.billing.domain.PaymentMethod;
import pe.com.perubilling.cpe.domain.DocumentBundle;
import pe.com.perubilling.issuer.domain.IssuerEntity;

class UblGeneratorPaymentTest {
    private final UblGenerator generator = new UblGenerator(new AmountInWords());

    @Test
    void emitsCreditPaymentTermsAndInstallments() {
        var doc = baseInvoice();
        doc.setPaymentMethod(PaymentMethod.CREDITO);
        doc.setPendingAmount(new BigDecimal("118.00"));

        var installment = new PaymentInstallmentEntity();
        installment.setInstallmentNumber(1);
        installment.setDueDate(LocalDate.of(2026, 10, 12));
        installment.setAmount(new BigDecimal("118.00"));

        String xml = new String(generator.generate(
                new DocumentBundle(doc, issuer(), List.of(item("10", false, "18.00", "18.00")), List.of(installment))),
                java.nio.charset.StandardCharsets.UTF_8);

        assertTrue(xml.contains("<cbc:ID>FormaPago</cbc:ID>"));
        assertTrue(xml.contains("<cbc:PaymentMeansID>Credito</cbc:PaymentMeansID>"));
        assertTrue(xml.contains("<cbc:PaymentMeansID>Cuota001</cbc:PaymentMeansID>"));
        assertTrue(xml.contains("<cbc:PaymentDueDate>2026-10-12</cbc:PaymentDueDate>"));
    }

    @Test
    void freeOperationUsesReferencePriceButZeroSalePrice() {
        var doc = baseInvoice();
        doc.setTaxableAmount(BigDecimal.ZERO);
        doc.setFreeAmount(new BigDecimal("100.00"));
        doc.setIgvAmount(BigDecimal.ZERO);
        doc.setFreeTaxAmount(new BigDecimal("18.00"));
        doc.setTotalAmount(BigDecimal.ZERO);
        doc.setPaymentMethod(PaymentMethod.CONTADO);
        doc.setPendingAmount(BigDecimal.ZERO);

        String xml = new String(generator.generate(
                new DocumentBundle(doc, issuer(), List.of(item("11", true, "18.00", "0.00")), List.of())),
                java.nio.charset.StandardCharsets.UTF_8);

        assertTrue(xml.matches("(?s).*<cbc:PriceTypeCode[^>]*>02</cbc:PriceTypeCode>.*"));
        assertTrue(xml.contains("<cbc:PriceAmount currencyID=\"PEN\">0.000000</cbc:PriceAmount>"));
        // 9996 conserva el impuesto referencial gratuito; la línea código 11 no incrementa el IGV pagable.
        assertTrue(xml.contains(">9996</cbc:ID>"));
        assertTrue(xml.contains("<cbc:TaxAmount currencyID=\"PEN\">18.00</cbc:TaxAmount>"));
        assertTrue(xml.matches("(?s).*<cbc:TaxExemptionReasonCode[^>]*>11</cbc:TaxExemptionReasonCode>.*?<cbc:ID[^>]*>1000</cbc:ID>.*"));
    }

    @Test
    void ivapUsesTaxScheme1016() {
        var doc = baseInvoice();
        doc.setTaxableAmount(BigDecimal.ZERO);
        doc.setIvapTaxableAmount(new BigDecimal("100.00"));
        doc.setIgvAmount(BigDecimal.ZERO);
        doc.setIvapAmount(new BigDecimal("4.00"));
        doc.setTotalAmount(new BigDecimal("104.00"));
        doc.setPaymentMethod(PaymentMethod.CONTADO);
        doc.setPendingAmount(BigDecimal.ZERO);

        String xml = new String(generator.generate(
                new DocumentBundle(doc, issuer(), List.of(item("17", false, "4.00", "4.00")), List.of())),
                java.nio.charset.StandardCharsets.UTF_8);

        assertTrue(xml.contains(">1016</cbc:ID>"));
        assertTrue(xml.contains("<cbc:Name>IVAP</cbc:Name>"));
    }

    @Test
    void emitsSunatTaxCategoryAndCatalogAttributes() {
        var doc = baseInvoice();
        doc.setPaymentMethod(PaymentMethod.CONTADO);
        doc.setPendingAmount(BigDecimal.ZERO);

        String xml = new String(generator.generate(
                new DocumentBundle(doc, issuer(), List.of(item("10", false, "18.00", "18.00")), List.of())),
                java.nio.charset.StandardCharsets.UTF_8);

        assertTrue(xml.contains("schemeID=\"UN/ECE 5305\""));
        assertTrue(xml.contains("schemeName=\"Tax Category Identifier\""));
        assertTrue(xml.contains(">S</cbc:ID>"));
        assertTrue(xml.contains("listURI=\"urn:pe:gob:sunat:cpe:see:gem:catalogos:catalogo07\""));
        assertTrue(xml.contains("listURI=\"urn:pe:gob:sunat:cpe:see:gem:catalogos:catalogo16\""));
        assertTrue(xml.contains("unitCodeListID=\"UN/ECE rec 20\""));
        assertTrue(xml.contains("schemeID=\"UN/ECE 5153\""));
    }

    @Test
    void issueTimeAlwaysIncludesSecondsRequiredBySunat() {
        var doc = baseInvoice();
        doc.setIssueTime(java.time.LocalTime.of(10, 30));
        doc.setPaymentMethod(PaymentMethod.CONTADO);
        doc.setPendingAmount(BigDecimal.ZERO);

        String xml = new String(generator.generate(
                new DocumentBundle(doc, issuer(), List.of(item("10", false, "18.00", "18.00")), List.of())),
                java.nio.charset.StandardCharsets.UTF_8);

        assertTrue(xml.contains("<cbc:IssueTime>10:30:00</cbc:IssueTime>"));
    }

    private ElectronicDocumentEntity baseInvoice() {
        var d = new ElectronicDocumentEntity();
        d.setDocumentType(DocumentType.INVOICE);
        d.setSeries("F001");
        d.setCorrelativo(1);
        d.setFullNumber("F001-1");
        d.setIssueDate(LocalDate.of(2026, 9, 12));
        d.setIssueTime(java.time.LocalTime.of(10, 30, 15));
        d.setOperationType("0101");
        d.setCurrency("PEN");
        d.setCustomerDocumentType("6");
        d.setCustomerDocumentNumber("20123456786");
        d.setCustomerName("CLIENTE SAC");
        d.setTaxableAmount(new BigDecimal("100.00"));
        d.setIvapTaxableAmount(BigDecimal.ZERO);
        d.setExoneratedAmount(BigDecimal.ZERO);
        d.setUnaffectedAmount(BigDecimal.ZERO);
        d.setExportAmount(BigDecimal.ZERO);
        d.setFreeAmount(BigDecimal.ZERO);
        d.setIgvAmount(new BigDecimal("18.00"));
        d.setIvapAmount(BigDecimal.ZERO);
        d.setFreeTaxAmount(BigDecimal.ZERO);
        d.setIcbperAmount(BigDecimal.ZERO);
        d.setTotalAmount(new BigDecimal("118.00"));
        return d;
    }

    private ElectronicDocumentItemEntity item(String affectation, boolean free, String rate, String tax) {
        var i = new ElectronicDocumentItemEntity();
        i.setLineNumber(1);
        i.setDescription("Producto");
        i.setUnitCode("NIU");
        i.setQuantity(BigDecimal.ONE);
        i.setUnitValue(new BigDecimal("100.000000"));
        i.setUnitPrice(free ? BigDecimal.ZERO.setScale(6) : new BigDecimal("118.000000"));
        i.setTaxAffectationCode(affectation);
        i.setIgvRate(new BigDecimal(rate));
        i.setLineBaseAmount(new BigDecimal("100.00"));
        i.setLineIgvAmount(new BigDecimal(tax));
        i.setIcbperPerUnit(BigDecimal.ZERO);
        i.setLineIcbperAmount(BigDecimal.ZERO);
        i.setLineTotalAmount(free ? BigDecimal.ZERO : new BigDecimal(affectation.equals("17") ? "104.00" : "118.00"));
        i.setFreeOperation(free);
        return i;
    }

    private IssuerEntity issuer() {
        var i = new IssuerEntity();
        i.setRuc("20123456786");
        i.setBusinessName("EMISOR SAC");
        i.setAddress("Lima");
        i.setUbigeo("150101");
        return i;
    }
}
