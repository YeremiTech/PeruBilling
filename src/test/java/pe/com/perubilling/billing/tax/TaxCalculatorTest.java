package pe.com.perubilling.billing.tax;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import java.math.BigDecimal;
import java.util.List;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import pe.com.perubilling.billing.api.AllowanceChargeRequest;
import pe.com.perubilling.billing.api.DocumentItemRequest;
import pe.com.perubilling.shared.domain.BusinessException;

class TaxCalculatorTest {
    private final TaxCalculator calculator = new TaxCalculator();

    @Test
    void calculatesTaxableIgvAtEighteenPercent() {
        var result = calculator.calculate(List.of(item("10", "100", "2", "18")));
        assertEquals(new BigDecimal("200.00"), result.taxable());
        assertEquals(new BigDecimal("36.00"), result.igv());
        assertEquals(new BigDecimal("236.00"), result.total());
    }

    @Test
    void calculatesFreeTaxWithoutIncreasingPayableTotal() {
        var result = calculator.calculate(List.of(item("11", "100", "1", "18")));
        assertEquals(new BigDecimal("100.00"), result.free());
        assertEquals(new BigDecimal("18.00"), result.freeTax());
        assertEquals(new BigDecimal("0.00"), result.igv());
        assertEquals(new BigDecimal("0.00"), result.total());
        assertEquals(new BigDecimal("0.00"), result.items().getFirst().lineTax());
        assertEquals(new BigDecimal("0.000000"), result.items().getFirst().unitPrice());
    }

    @Test
    void calculatesIvapAtFourPercentSeparatelyFromIgv() {
        var result = calculator.calculate(List.of(item("17", "100", "1", "4")));
        assertEquals(new BigDecimal("100.00"), result.ivapTaxable());
        assertEquals(new BigDecimal("4.00"), result.ivap());
        assertEquals(new BigDecimal("0.00"), result.igv());
        assertEquals(new BigDecimal("104.00"), result.total());
    }

    @Test
    void calculatesExoneratedWithoutIgv() {
        var result = calculator.calculate(List.of(item("20", "50", "1", "0")));
        assertEquals(new BigDecimal("50.00"), result.exonerated());
        assertEquals(new BigDecimal("0.00"), result.igv());
        assertEquals(new BigDecimal("50.00"), result.total());
    }

    @Test
    void calculatesFreeUnaffectedWithoutTax() {
        var result = calculator.calculate(List.of(item("35", "30", "1", "0")));
        assertEquals(new BigDecimal("30.00"), result.free());
        assertEquals(new BigDecimal("0.00"), result.freeTax());
        assertEquals(new BigDecimal("0.00"), result.total());
    }

    @Test
    void calculatesExportWithoutTax() {
        var result = calculator.calculate(List.of(item("40", "80", "1", "0")));
        assertEquals(new BigDecimal("80.00"), result.exportAmount());
        assertEquals(new BigDecimal("80.00"), result.total());
    }

    @Test
    void rejectsIvapWithIgvRate() {
        assertThrows(BusinessException.class,
                () -> calculator.calculate(List.of(item("17", "100", "1", "18"))));
    }

    @Test
    void rejectsUnsupportedIgvRateInsteadOfEmittingUncalibratedXml() {
        assertThrows(BusinessException.class,
                () -> calculator.calculate(List.of(item("10", "100", "1", "10"))));
    }

    @Test
    void appliesLineDiscountBeforeIgvAndReportsAllowanceTotal() {
        var request = new DocumentItemRequest(
                "SKU", "Producto", "NIU", BigDecimal.ONE, new BigDecimal("100.00"),
                "10", new BigDecimal("18.00"), BigDecimal.ZERO,
                List.of(new AllowanceChargeRequest(false, "00", new BigDecimal("0.10"))));

        var result = calculator.calculate(List.of(request));

        assertEquals(new BigDecimal("10.00"), result.allowanceTotal());
        assertEquals(new BigDecimal("90.00"), result.taxable());
        assertEquals(new BigDecimal("16.20"), result.igv());
        assertEquals(new BigDecimal("106.20"), result.total());
        assertEquals(new BigDecimal("90.00"), result.items().getFirst().lineBase());
        assertEquals(new BigDecimal("106.200000"), result.items().getFirst().unitPrice());
    }

    @Test
    void addsNonTaxableLineChargeWithoutIncreasingTaxBase() {
        var request = new DocumentItemRequest(
                "SKU", "Producto", "NIU", BigDecimal.ONE, new BigDecimal("100.00"),
                "10", new BigDecimal("18.00"), BigDecimal.ZERO,
                List.of(new AllowanceChargeRequest(true, "48", new BigDecimal("0.10"))));

        var result = calculator.calculate(List.of(request));

        assertEquals(new BigDecimal("10.00"), result.chargeTotal());
        assertEquals(new BigDecimal("100.00"), result.taxable());
        assertEquals(new BigDecimal("18.00"), result.igv());
        assertEquals(new BigDecimal("128.00"), result.total());
    }

    @Test
    void rejectsTaxModulesNotYetEnabledThroughCatalog53() {
        var request = new DocumentItemRequest(
                "SKU", "Producto", "NIU", BigDecimal.ONE, new BigDecimal("100.00"),
                "10", new BigDecimal("18.00"), BigDecimal.ZERO,
                List.of(new AllowanceChargeRequest(false, "01", new BigDecimal("0.10"))));

        assertThrows(BusinessException.class, () -> calculator.calculate(List.of(request)));
    }

    @Test
    void rejectsAllowanceChargeIndicatorMismatch() {
        var request = new DocumentItemRequest(
                "SKU", "Producto", "NIU", BigDecimal.ONE, new BigDecimal("100.00"),
                "10", new BigDecimal("18.00"), BigDecimal.ZERO,
                List.of(new AllowanceChargeRequest(true, "00", new BigDecimal("0.10"))));

        assertThrows(BusinessException.class, () -> calculator.calculate(List.of(request)));
    }

    @Test
    void appliesGlobalDiscountAndChargeWithoutChangingTaxBase() {
        var item = new DocumentItemRequest(
                "SKU", "Producto", "NIU", BigDecimal.ONE, new BigDecimal("100.00"),
                "10", new BigDecimal("18.00"), BigDecimal.ZERO);
        var result = calculator.calculate(
                List.of(item),
                List.of(
                        new AllowanceChargeRequest(false, "03", new BigDecimal("0.10")),
                        new AllowanceChargeRequest(true, "50", new BigDecimal("0.05"))),
                LocalDate.of(2026, 9, 12));

        assertEquals(new BigDecimal("100.00"), result.taxable());
        assertEquals(new BigDecimal("18.00"), result.igv());
        assertEquals(new BigDecimal("11.80"), result.allowanceTotal());
        assertEquals(new BigDecimal("5.90"), result.chargeTotal());
        assertEquals(new BigDecimal("112.10"), result.total());
        assertEquals(2, result.documentAdjustments().size());
    }

    @Test
    void validatesIcbperAgainstIssueDate() {
        var item = new DocumentItemRequest(
                "BOLSA", "Bolsa", "NIU", BigDecimal.ONE, new BigDecimal("1.00"),
                "10", new BigDecimal("18.00"), new BigDecimal("0.50"));
        var result = calculator.calculate(List.of(item), List.of(), LocalDate.of(2026, 9, 12));
        assertEquals(new BigDecimal("0.50"), result.icbper());
        assertEquals(new BigDecimal("0.5000"), result.items().getFirst().appliedIcbperPerUnit());
    }

    @Test
    void rejectsArbitraryIcbperRate() {
        var item = new DocumentItemRequest(
                "BOLSA", "Bolsa", "NIU", BigDecimal.ONE, new BigDecimal("1.00"),
                "10", new BigDecimal("18.00"), new BigDecimal("0.30"));
        assertThrows(BusinessException.class,
                () -> calculator.calculate(List.of(item), List.of(), LocalDate.of(2026, 9, 12)));
    }

    private DocumentItemRequest item(String affectation, String value, String quantity, String rate) {
        return new DocumentItemRequest("SKU", "Producto", "NIU",
                new BigDecimal(quantity), new BigDecimal(value), affectation,
                new BigDecimal(rate), BigDecimal.ZERO);
    }
}
