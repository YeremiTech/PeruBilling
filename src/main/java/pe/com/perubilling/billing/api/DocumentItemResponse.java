package pe.com.perubilling.billing.api;

import java.math.BigDecimal;
import java.util.List;

public record DocumentItemResponse(
        int lineNumber,
        String sku,
        String description,
        String unitCode,
        BigDecimal quantity,
        BigDecimal unitValue,
        BigDecimal unitPrice,
        String taxAffectationCode,
        BigDecimal igvRate,
        BigDecimal lineGrossAmount,
        BigDecimal lineAllowanceAmount,
        BigDecimal lineChargeAmount,
        BigDecimal lineBaseAmount,
        BigDecimal lineIgvAmount,
        BigDecimal lineIcbperAmount,
        BigDecimal lineTotalAmount,
        boolean freeOperation,
        List<AllowanceChargeResponse> adjustments
) {}
