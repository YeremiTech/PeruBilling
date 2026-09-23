package pe.com.perubilling.delivery.api;

import java.math.BigDecimal;
import java.time.LocalDate;

public record PublicDocumentResponse(
        String documentType,
        String number,
        LocalDate issueDate,
        String issuerRuc,
        String issuerName,
        String currency,
        BigDecimal totalAmount,
        String status) {}
