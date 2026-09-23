package pe.com.perubilling.billing.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record ReferenceDocumentRequest(
        @NotBlank @Pattern(regexp = "\\d{2}") String documentType,
        @NotBlank @Size(max = 30) String number,
        @NotBlank @Pattern(regexp = "\\d{2}") String reasonCode,
        @NotBlank @Size(max = 500) String reason
) {}
