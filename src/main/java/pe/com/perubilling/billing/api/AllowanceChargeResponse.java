package pe.com.perubilling.billing.api;

import java.math.BigDecimal;

public record AllowanceChargeResponse(
        int sequenceNumber,
        boolean charge,
        String reasonCode,
        BigDecimal factor,
        BigDecimal amount,
        BigDecimal baseAmount
) {}
