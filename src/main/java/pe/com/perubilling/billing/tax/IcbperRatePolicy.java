package pe.com.perubilling.billing.tax;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import org.springframework.stereotype.Component;
import pe.com.perubilling.shared.domain.BusinessException;

/** Política ICBPER versionada por fecha de emisión. */
@Component
public class IcbperRatePolicy {
    public BigDecimal expectedRate(LocalDate issueDate) {
        if (issueDate == null || issueDate.getYear() < 2019) return zero();
        return switch (issueDate.getYear()) {
            case 2019 -> rate("0.10");
            case 2020 -> rate("0.20");
            case 2021 -> rate("0.30");
            case 2022 -> rate("0.40");
            default -> rate("0.50");
        };
    }

    public BigDecimal validateAndResolve(LocalDate issueDate, BigDecimal requestedRate) {
        if (requestedRate == null || requestedRate.signum() == 0) return zero();
        if (requestedRate.signum() < 0) {
            throw BusinessException.badRequest("INVALID_ICBPER_RATE", "ICBPER no puede ser negativo");
        }
        BigDecimal expected = expectedRate(issueDate);
        if (expected.signum() == 0 || requestedRate.setScale(4, RoundingMode.HALF_UP).compareTo(expected) != 0) {
            throw BusinessException.badRequest("INVALID_ICBPER_RATE",
                    "La tasa ICBPER no corresponde a la fecha de emisión. Esperada: " + expected.toPlainString());
        }
        return expected;
    }

    private BigDecimal rate(String value) { return new BigDecimal(value).setScale(4, RoundingMode.HALF_UP); }
    private BigDecimal zero() { return BigDecimal.ZERO.setScale(4, RoundingMode.HALF_UP); }
}
