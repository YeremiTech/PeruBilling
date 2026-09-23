package pe.com.perubilling.issuer.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import pe.com.perubilling.issuer.domain.SunatEnvironment;

public record CreateIssuerRequest(
        @NotBlank @Pattern(regexp = "\\d{11}") String ruc,
        @NotBlank @Size(max = 250) String businessName,
        @Size(max = 250) String tradeName,
        @NotBlank @Size(max = 250) String address,
        @NotBlank @Pattern(regexp = "\\d{6}") String ubigeo,
        @Pattern(regexp = "\\d{4}") String establishmentCode,
        @Size(max = 100) String department,
        @Size(max = 100) String province,
        @Size(max = 100) String district,
        SunatEnvironment environment
) {}
