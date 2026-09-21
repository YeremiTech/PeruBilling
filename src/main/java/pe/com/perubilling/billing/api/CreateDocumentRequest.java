package pe.com.perubilling.billing.api;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

@Schema(description = "Solicitud de emisión de un comprobante electrónico")
public record CreateDocumentRequest(
        @Schema(description = "Emisor configurado en PeruBilling") @NotNull UUID issuerId,
        @Schema(description = "Identificador idempotente de negocio del ERP/POS", example = "SALE-2026-000184") @Size(max = 100) String externalId,
        @Schema(example = "F001") @NotBlank @Pattern(regexp = "[FB][A-Z0-9]{3}") String series,
        LocalDate issueDate,
        LocalTime issueTime,
        @Schema(description = "Tipo de operación SUNAT", example = "0101") @Pattern(regexp = "\\d{4}") String operationType,
        @Schema(description = "Moneda ISO 4217", example = "PEN") @NotBlank @Pattern(regexp = "[A-Z]{3}") String currency,
        @Valid CustomerRequest customer,
        @NotEmpty @Size(max = 500) List<@Valid DocumentItemRequest> items,
        @Size(max = 10) List<@Valid AllowanceChargeRequest> adjustments,
        @Valid ReferenceDocumentRequest reference,
        @Valid PaymentRequest payment
) {
    public CreateDocumentRequest(
            UUID issuerId,
            String externalId,
            String series,
            LocalDate issueDate,
            LocalTime issueTime,
            String operationType,
            String currency,
            CustomerRequest customer,
            List<DocumentItemRequest> items,
            ReferenceDocumentRequest reference,
            PaymentRequest payment) {
        this(issuerId, externalId, series, issueDate, issueTime, operationType, currency,
                customer, items, List.of(), reference, payment);
    }

    public List<AllowanceChargeRequest> safeAdjustments() {
        return adjustments == null ? List.of() : List.copyOf(adjustments);
    }
}
