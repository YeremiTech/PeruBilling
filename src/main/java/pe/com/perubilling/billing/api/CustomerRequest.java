package pe.com.perubilling.billing.api;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

@Schema(description = "Adquirente o usuario del comprobante")
public record CustomerRequest(
        @Schema(example = "6", description = "Código SUNAT Catálogo 06")
        @NotBlank @Pattern(regexp = "[0-9A-Z]{1,2}") String documentType,
        @Schema(example = "20123456789")
        @NotBlank @Size(max = 20) String documentNumber,
        @Schema(example = "EMPRESA CLIENTE SAC")
        @NotBlank @Size(max = 300) String name,
        @Size(max = 300) String address,
        @Email @Size(max = 255) String email,
        @Schema(example = "PE", description = "ISO 3166-1 alpha-2. Obligatorio para exportación 0102")
        @Pattern(regexp = "[A-Za-z]{2}") String countryCode
) {
    public CustomerRequest(String documentType, String documentNumber, String name, String address, String email) {
        this(documentType, documentNumber, name, address, email, null);
    }
}
