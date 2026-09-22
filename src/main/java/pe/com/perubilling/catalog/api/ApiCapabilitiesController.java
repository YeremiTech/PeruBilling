package pe.com.perubilling.catalog.api;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;

import io.swagger.v3.oas.annotations.Operation;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pe.com.perubilling.shared.sunat.SunatRulesBaseline;

@RestController
@RequestMapping("/api/v1/capabilities")
@ApiResponses({
        @ApiResponse(responseCode = "400", ref = "#/components/responses/BadRequest"),
        @ApiResponse(responseCode = "401", ref = "#/components/responses/Unauthorized"),
        @ApiResponse(responseCode = "403", ref = "#/components/responses/Forbidden"),
        @ApiResponse(responseCode = "404", ref = "#/components/responses/NotFound"),
        @ApiResponse(responseCode = "409", ref = "#/components/responses/Conflict"),
        @ApiResponse(responseCode = "422", ref = "#/components/responses/UnprocessableEntity"),
        @ApiResponse(responseCode = "429", ref = "#/components/responses/TooManyRequests"),
        @ApiResponse(responseCode = "500", ref = "#/components/responses/InternalServerError")
})
public class ApiCapabilitiesController {
    @Operation(summary = "Obtiene la matriz de capacidades funcionales del motor")
    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','OPERATOR','VIEWER') or hasAuthority('SCOPE_DOCUMENT_READ')")
    public ApiCapabilitiesResponse get() {
        return new ApiCapabilitiesResponse("v1", SunatRulesBaseline.VERSION, List.of(
                capability("INVOICE_0101", "Factura venta interna", true, true),
                capability("INVOICE_0102", "Factura de exportación", true, true),
                capability("INVOICE_0107", "Factura IVAP", true, true),
                capability("RECEIPT_03", "Boleta electrónica", true, true),
                capability("CREDIT_NOTE_07", "Nota de crédito", true, true),
                capability("DEBIT_NOTE_08", "Nota de débito", true, true),
                capability("DAILY_SUMMARY", "Resumen Diario", true, true),
                capability("VOIDING", "Comunicación de Baja", true, true),
                capability("LINE_ALLOWANCES_CHARGES", "Descuentos y cargos de línea CORE", true, true),
                capability("GLOBAL_ALLOWANCES_CHARGES", "Descuentos y cargos globales CORE", true, true),
                capability("PDF_A4", "Representación PDF A4 profesional", true, false),
                capability("PDF_THERMAL_80", "Representación PDF térmica de 80 mm para POS", true, false),
                capability("DETRACTION", "Detracciones / SPOT", false, true),
                capability("ISC", "Impuesto Selectivo al Consumo", false, true),
                capability("ADVANCE_PAYMENT", "Anticipos", false, true),
                capability("PERCEPTION", "Percepciones", false, true),
                capability("RETENTION", "Retenciones", false, true),
                capability("OSE", "Proveedor OSE externo", false, true)));
    }

    private ApiCapabilitiesResponse.Capability capability(
            String code, String description, boolean supported, boolean requiresRegulatoryEvidence) {
        return new ApiCapabilitiesResponse.Capability(code, description, supported, requiresRegulatoryEvidence);
    }
}
