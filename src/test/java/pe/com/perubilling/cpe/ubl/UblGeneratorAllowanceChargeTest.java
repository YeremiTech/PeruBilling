package pe.com.perubilling.cpe.ubl;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import pe.com.perubilling.billing.domain.AllowanceChargeEntity;
import pe.com.perubilling.billing.domain.ElectronicDocumentEntity;
import pe.com.perubilling.billing.domain.DocumentAllowanceChargeEntity;
import pe.com.perubilling.billing.domain.ElectronicDocumentItemEntity;
import pe.com.perubilling.billing.domain.PaymentMethod;
import pe.com.perubilling.cpe.domain.DocumentBundle;
import pe.com.perubilling.issuer.domain.IssuerEntity;
import pe.com.perubilling.shared.domain.DocumentType;

class UblGeneratorAllowanceChargeTest {
    private final UblGenerator generator = new UblGenerator(new AmountInWords());

    @Test
    void emitsLineDiscountAndChargeWithConsistentLegalTotals() {
        UUID itemId = UUID.randomUUID();
        var document = document();
        var item = item(itemId);
        var discount = adjustment(itemId, 1, false, "00", "0.100000", "10.00");
        var charge = adjustment(itemId, 2, true, "48", "0.100000", "10.00");

        String xml = new String(generator.generate(new DocumentBundle(
                document, issuer(), List.of(item), List.of(), List.of(discount, charge))), StandardCharsets.UTF_8);

        assertTrue(xml.contains("<cbc:AllowanceTotalAmount currencyID=\"PEN\">10.00</cbc:AllowanceTotalAmount>"));
        assertTrue(xml.contains("<cbc:ChargeTotalAmount currencyID=\"PEN\">10.00</cbc:ChargeTotalAmount>"));
        assertTrue(xml.contains("<cbc:TaxInclusiveAmount currencyID=\"PEN\">106.20</cbc:TaxInclusiveAmount>"));
        assertTrue(xml.contains("<cbc:PayableAmount currencyID=\"PEN\">116.20</cbc:PayableAmount>"));
        assertTrue(xml.contains("<cbc:AllowanceChargeReasonCode"));
        assertTrue(xml.contains(">00</cbc:AllowanceChargeReasonCode>"));
        assertTrue(xml.contains(">48</cbc:AllowanceChargeReasonCode>"));
        assertTrue(xml.contains("<cbc:MultiplierFactorNumeric>0.1</cbc:MultiplierFactorNumeric>"));
        assertTrue(xml.contains("<cbc:LineExtensionAmount currencyID=\"PEN\">90.00</cbc:LineExtensionAmount>"));
        assertTrue(xml.contains("<cbc:PriceAmount currencyID=\"PEN\">106.200000</cbc:PriceAmount>"));
    }


    @Test
    void emitsGlobalDiscountAndChargeAtDocumentLevel() {
        UUID itemId = UUID.randomUUID();
        var document = document();
        document.setAllowanceTotalAmount(new BigDecimal("21.62"));
        document.setChargeTotalAmount(new BigDecimal("15.81"));
        document.setTotalAmount(new BigDecimal("110.39"));
        var item = item(itemId);
        var lineDiscount = adjustment(itemId, 1, false, "00", "0.100000", "10.00");
        var lineCharge = adjustment(itemId, 2, true, "48", "0.100000", "10.00");
        var globalDiscount = documentAdjustment(1, false, "03", "0.100000", "11.62", "116.20");
        var globalCharge = documentAdjustment(2, true, "50", "0.050000", "5.81", "116.20");

        String xml = new String(generator.generate(new DocumentBundle(
                document, issuer(), List.of(item), List.of(), List.of(lineDiscount, lineCharge),
                List.of(globalDiscount, globalCharge))), StandardCharsets.UTF_8);

        assertTrue(xml.contains(">03</cbc:AllowanceChargeReasonCode>"));
        assertTrue(xml.contains(">50</cbc:AllowanceChargeReasonCode>"));
        assertTrue(xml.contains("<cbc:PayableAmount currencyID=\"PEN\">110.39</cbc:PayableAmount>"));
    }

    @Test
    void emitsSunatProductCodeAndGtinWhenProvided() {
        UUID itemId = UUID.randomUUID();
        var document = document();
        var item = item(itemId);
        item.setSunatProductCode("43211508");
        item.setGtin("12345678");
        item.setGtinSchemeId("GTIN-8");

        String xml = new String(generator.generate(new DocumentBundle(
                document, issuer(), List.of(item), List.of(), List.of())), StandardCharsets.UTF_8);

        assertTrue(xml.contains("listID=\"UNSPSC\""));
        assertTrue(xml.contains(">43211508</cbc:ItemClassificationCode>"));
        assertTrue(xml.contains("schemeID=\"GTIN-8\""));
        assertTrue(xml.contains(">12345678</cbc:ID>"));
    }

    @Test
    void emitsExportProfileCountryAndExportTaxScheme() {
        UUID itemId = UUID.randomUUID();
        var document = document();
        document.setOperationType("0102");
        document.setCurrency("USD");
        document.setCustomerDocumentType("0");
        document.setCustomerDocumentNumber("TAX-123");
        document.setCustomerName("FOREIGN CUSTOMER");
        document.setCustomerCountryCode("US");
        document.setTaxableAmount(BigDecimal.ZERO);
        document.setIgvAmount(BigDecimal.ZERO);
        document.setAllowanceTotalAmount(BigDecimal.ZERO);
        document.setChargeTotalAmount(BigDecimal.ZERO);
        document.setExportAmount(new BigDecimal("100.00"));
        document.setTotalAmount(new BigDecimal("100.00"));

        var item = item(itemId);
        item.setUnitValue(new BigDecimal("100.000000"));
        item.setUnitPrice(new BigDecimal("100.000000"));
        item.setTaxAffectationCode("40");
        item.setIgvRate(BigDecimal.ZERO);
        item.setLineGrossAmount(new BigDecimal("100.00"));
        item.setLineAllowanceAmount(BigDecimal.ZERO);
        item.setLineChargeAmount(BigDecimal.ZERO);
        item.setLineBaseAmount(new BigDecimal("100.00"));
        item.setLineIgvAmount(BigDecimal.ZERO);
        item.setLineTotalAmount(new BigDecimal("100.00"));

        String xml = new String(generator.generate(new DocumentBundle(
                document, issuer(), List.of(item), List.of())), StandardCharsets.UTF_8);

        assertTrue(xml.contains(">0102</cbc:ProfileID>"));
        assertTrue(xml.contains(">US</cbc:IdentificationCode>"));
        assertTrue(xml.contains(">9995</cbc:ID>"));
        assertTrue(xml.contains(">EXP</cbc:Name>"));
        assertTrue(xml.contains("<cbc:TaxAmount currencyID=\"USD\">0.00</cbc:TaxAmount>"));
    }

    private ElectronicDocumentEntity document() {
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
        d.setChargeTotalAmount(new BigDecimal("10.00"));
        d.setTotalAmount(new BigDecimal("116.20"));
        d.setPaymentMethod(PaymentMethod.CONTADO);
        d.setPendingAmount(BigDecimal.ZERO);
        return d;
    }

    private ElectronicDocumentItemEntity item(UUID id) {
        var i = new ElectronicDocumentItemEntity();
        i.setId(id);
        i.setLineNumber(1);
        i.setDescription("Producto");
        i.setUnitCode("NIU");
        i.setQuantity(BigDecimal.ONE);
        i.setUnitValue(new BigDecimal("100.000000"));
        i.setUnitPrice(new BigDecimal("106.200000"));
        i.setTaxAffectationCode("10");
        i.setIgvRate(new BigDecimal("18.00"));
        i.setLineGrossAmount(new BigDecimal("100.00"));
        i.setLineAllowanceAmount(new BigDecimal("10.00"));
        i.setLineChargeAmount(new BigDecimal("10.00"));
        i.setLineBaseAmount(new BigDecimal("90.00"));
        i.setLineIgvAmount(new BigDecimal("16.20"));
        i.setIcbperPerUnit(BigDecimal.ZERO);
        i.setLineIcbperAmount(BigDecimal.ZERO);
        i.setLineTotalAmount(new BigDecimal("116.20"));
        i.setFreeOperation(false);
        return i;
    }

    private AllowanceChargeEntity adjustment(
            UUID itemId, int sequence, boolean charge, String reasonCode, String factor, String amount) {
        var a = new AllowanceChargeEntity();
        a.setDocumentItemId(itemId);
        a.setLineNumber(1);
        a.setSequenceNumber(sequence);
        a.setCharge(charge);
        a.setReasonCode(reasonCode);
        a.setFactor(new BigDecimal(factor));
        a.setAmount(new BigDecimal(amount));
        a.setBaseAmount(new BigDecimal("100.00"));
        return a;
    }


    private DocumentAllowanceChargeEntity documentAdjustment(
            int sequence, boolean charge, String reasonCode, String factor, String amount, String baseAmount) {
        var a = new DocumentAllowanceChargeEntity();
        a.setSequenceNumber(sequence);
        a.setCharge(charge);
        a.setReasonCode(reasonCode);
        a.setFactor(new BigDecimal(factor));
        a.setAmount(new BigDecimal(amount));
        a.setBaseAmount(new BigDecimal(baseAmount));
        return a;
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
