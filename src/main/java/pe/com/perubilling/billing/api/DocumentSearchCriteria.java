package pe.com.perubilling.billing.api;

import java.time.LocalDate;
import java.util.UUID;
import pe.com.perubilling.billing.domain.DocumentStatus;
import pe.com.perubilling.shared.domain.DocumentType;

public record DocumentSearchCriteria(
        UUID issuerId,
        DocumentType documentType,
        String operationType,
        String series,
        String number,
        String externalId,
        DocumentStatus status,
        String customerDocumentNumber,
        LocalDate fromIssueDate,
        LocalDate toIssueDate
) {}
