package pe.com.perubilling.billing.tax;

import java.math.BigDecimal;
import java.util.List;
import pe.com.perubilling.billing.api.DocumentItemRequest;
import pe.com.perubilling.billing.domain.TaxAffectation;

public record TaxCalculation(
        BigDecimal taxable,
        BigDecimal ivapTaxable,
        BigDecimal exonerated,
        BigDecimal unaffected,
        BigDecimal exportAmount,
        BigDecimal free,
        BigDecimal igv,
        BigDecimal ivap,
        BigDecimal freeTax,
        BigDecimal icbper,
        BigDecimal allowanceTotal,
        BigDecimal chargeTotal,
        BigDecimal total,
        List<CalculatedItem> items,
        List<CalculatedAdjustment> documentAdjustments
) {
    public record CalculatedItem(
            DocumentItemRequest source,
            int lineNumber,
            BigDecimal unitPrice,
            BigDecimal lineGross,
            BigDecimal lineAllowance,
            BigDecimal lineCharge,
            BigDecimal lineBase,
            BigDecimal lineTax,
            BigDecimal appliedIcbperPerUnit,
            BigDecimal lineIcbper,
            BigDecimal lineTotal,
            boolean freeOperation,
            BigDecimal appliedTaxRate,
            TaxAffectation.TaxKind taxKind,
            List<CalculatedAdjustment> adjustments
    ) {}

    public record CalculatedAdjustment(
            int sequenceNumber,
            boolean charge,
            String reasonCode,
            BigDecimal factor,
            BigDecimal amount,
            BigDecimal baseAmount
    ) {}
}
