package pe.com.perubilling.billing.api;

import java.time.Instant;
import pe.com.perubilling.billing.domain.DocumentStatus;

public record DocumentHistoryResponse(DocumentStatus status, String code, String message, Instant createdAt) {}
