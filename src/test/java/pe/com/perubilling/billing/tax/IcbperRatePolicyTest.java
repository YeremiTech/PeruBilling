package pe.com.perubilling.billing.tax;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import pe.com.perubilling.shared.domain.BusinessException;

class IcbperRatePolicyTest {
    private final IcbperRatePolicy policy = new IcbperRatePolicy();

    @Test
    void resolvesHistoricalRatesByIssueYear() {
        assertEquals(new BigDecimal("0.1000"), policy.expectedRate(LocalDate.of(2019, 1, 1)));
        assertEquals(new BigDecimal("0.2000"), policy.expectedRate(LocalDate.of(2020, 1, 1)));
        assertEquals(new BigDecimal("0.3000"), policy.expectedRate(LocalDate.of(2021, 1, 1)));
        assertEquals(new BigDecimal("0.4000"), policy.expectedRate(LocalDate.of(2022, 1, 1)));
        assertEquals(new BigDecimal("0.5000"), policy.expectedRate(LocalDate.of(2023, 1, 1)));
        assertEquals(new BigDecimal("0.5000"), policy.expectedRate(LocalDate.of(2026, 9, 12)));
    }

    @Test
    void resolvesZeroBeforeTaxWasEffective() {
        assertEquals(new BigDecimal("0.0000"), policy.expectedRate(LocalDate.of(2018, 12, 31)));
    }

    @Test
    void rejectsRateThatDoesNotBelongToIssueDate() {
        assertThrows(BusinessException.class,
                () -> policy.validateAndResolve(LocalDate.of(2026, 9, 12), new BigDecimal("0.40")));
    }

    @Test
    void acceptsZeroWhenLineDoesNotApplyIcbper() {
        assertEquals(new BigDecimal("0.0000"),
                policy.validateAndResolve(LocalDate.of(2026, 9, 12), BigDecimal.ZERO));
    }
}
