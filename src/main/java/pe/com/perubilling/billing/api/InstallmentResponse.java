package pe.com.perubilling.billing.api;

import java.math.BigDecimal;
import java.time.LocalDate;

public record InstallmentResponse(int number, LocalDate dueDate, BigDecimal amount) {}
