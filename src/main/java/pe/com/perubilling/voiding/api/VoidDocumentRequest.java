package pe.com.perubilling.voiding.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record VoidDocumentRequest(
        @NotBlank @Size(min=3,max=500) String reason
) {}
