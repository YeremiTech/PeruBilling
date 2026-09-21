package pe.com.perubilling.billing.api;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.List;

@Schema(description = "Línea del comprobante")
public record DocumentItemRequest(
        @Size(max = 100) String sku,
        @Pattern(regexp = "\\d{8}") String sunatProductCode,
        @Pattern(regexp = "\\d{8,14}") String gtin,
        @Size(max = 14) String gtinSchemeId,
        @NotBlank @Size(max = 500) String description,
        @NotBlank @Pattern(regexp = "[A-Z0-9]{2,3}") String unitCode,
        @DecimalMin(value = "0.000001") @Digits(integer = 12, fraction = 6) BigDecimal quantity,
        @DecimalMin(value = "0.00") @Digits(integer = 12, fraction = 6) BigDecimal unitValue,
        @NotBlank @Pattern(regexp = "\\d{2}") String taxAffectationCode,
        @DecimalMin(value = "0.00") @Digits(integer = 3, fraction = 4) BigDecimal igvRate,
        @DecimalMin(value = "0.00") @Digits(integer = 12, fraction = 4) BigDecimal icbperPerUnit,
        @Size(max = 10) List<@Valid AllowanceChargeRequest> adjustments
) {
    public DocumentItemRequest(
            String sku,
            String description,
            String unitCode,
            BigDecimal quantity,
            BigDecimal unitValue,
            String taxAffectationCode,
            BigDecimal igvRate,
            BigDecimal icbperPerUnit) {
        this(sku, null, null, null, description, unitCode, quantity, unitValue, taxAffectationCode, igvRate, icbperPerUnit, List.of());
    }

    public DocumentItemRequest(
            String sku,
            String description,
            String unitCode,
            BigDecimal quantity,
            BigDecimal unitValue,
            String taxAffectationCode,
            BigDecimal igvRate,
            BigDecimal icbperPerUnit,
            List<AllowanceChargeRequest> adjustments) {
        this(sku, null, null, null, description, unitCode, quantity, unitValue, taxAffectationCode, igvRate, icbperPerUnit, adjustments);
    }

    public List<AllowanceChargeRequest> safeAdjustments() {
        return adjustments == null ? List.of() : List.copyOf(adjustments);
    }
}
