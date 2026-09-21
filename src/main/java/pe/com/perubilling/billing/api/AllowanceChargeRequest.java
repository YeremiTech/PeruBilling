package pe.com.perubilling.billing.api;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.math.BigDecimal;

/**
 * Ajuste UBL a nivel de línea. El factor se expresa como fracción decimal:
 * 0.05 representa 5%.
 */
public record AllowanceChargeRequest(
        @NotNull Boolean charge,
        @NotBlank @Pattern(regexp = "\\d{2}") String reasonCode,
        @NotNull @DecimalMin(value = "0.000001") @DecimalMax(value = "1.000000")
        @Digits(integer = 1, fraction = 6) BigDecimal factor
) {}
