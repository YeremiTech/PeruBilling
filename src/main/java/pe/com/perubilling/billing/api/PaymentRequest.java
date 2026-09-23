package pe.com.perubilling.billing.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.List;
import pe.com.perubilling.billing.domain.PaymentMethod;

public record PaymentRequest(
        @NotNull PaymentMethod method,
        @DecimalMin(value = "0.00") @Digits(integer = 15, fraction = 2) BigDecimal pendingAmount,
        @Size(max = 60) List<@Valid InstallmentRequest> installments
) {}
