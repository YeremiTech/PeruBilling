package pe.com.perubilling.billing.api;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Adquirente persistido en el comprobante")
public record CustomerResponse(
        @Schema(example = "6") String documentType,
        @Schema(example = "20123456789") String documentNumber,
        @Schema(example = "EMPRESA CLIENTE SAC") String name,
        @Schema(example = "Av. Principal 123") String address,
        @Schema(example = "facturacion@cliente.com") String email,
        @Schema(example = "PE") String countryCode
) {}
