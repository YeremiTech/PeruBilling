package pe.com.perubilling.billing.api;

import java.math.BigDecimal;
import java.util.List;
import pe.com.perubilling.billing.domain.PaymentMethod;

public record PaymentResponse(PaymentMethod method, BigDecimal pendingAmount, List<InstallmentResponse> installments) {}
