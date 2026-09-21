package pe.com.perubilling.voiding.api;

import java.util.UUID;
import pe.com.perubilling.billing.domain.DocumentStatus;

public record VoidingResponse(UUID documentId, DocumentStatus status, String batchIdentifier, String message) {}
