package pe.com.perubilling.catalog.api;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;

import io.swagger.v3.oas.annotations.Operation;
import java.util.Arrays;
import java.util.Currency;
import java.util.Comparator;
import java.util.Locale;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pe.com.perubilling.billing.domain.TaxAffectation;
import pe.com.perubilling.shared.domain.DocumentType;
import pe.com.perubilling.shared.sunat.SunatNoteReasonCatalog;
import pe.com.perubilling.shared.sunat.SunatRulesBaseline;
import pe.com.perubilling.shared.sunat.catalog.SunatAllowanceChargeReason;
import pe.com.perubilling.shared.sunat.catalog.SunatIdentityDocumentType;
import pe.com.perubilling.shared.sunat.catalog.SunatOperationType;
import pe.com.perubilling.shared.sunat.catalog.SunatPriceType;
import pe.com.perubilling.shared.sunat.catalog.SunatTaxScheme;
import pe.com.perubilling.shared.sunat.catalog.SunatUnitCode;

@RestController
@RequestMapping("/api/v1/catalogs/sunat")
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
public class SunatCatalogController {
    @Operation(summary = "Obtiene catálogos SUNAT y el soporte real de esta versión")
    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','OPERATOR','VIEWER') or hasAuthority('SCOPE_DOCUMENT_READ')")
    public SunatCatalogResponse get() {
        return new SunatCatalogResponse(
                SunatRulesBaseline.VERSION,
                SunatRulesBaseline.COVERAGE,
                Arrays.stream(DocumentType.values())
                        .map(value -> new SunatCatalogResponse.CatalogEntry(value.getSunatCode(), value.name(), true))
                        .toList(),
                Arrays.stream(SunatIdentityDocumentType.values())
                        .map(value -> new SunatCatalogResponse.CatalogEntry(value.code(), value.description(), true))
                        .toList(),
                Arrays.stream(SunatOperationType.values())
                        .map(value -> new SunatCatalogResponse.CatalogEntry(value.code(), value.description(), value.coreSupported()))
                        .toList(),
                Arrays.stream(TaxAffectation.values())
                        .map(value -> new SunatCatalogResponse.CatalogEntry(value.code(), value.name(), true))
                        .toList(),
                Arrays.stream(SunatTaxScheme.values())
                        .map(value -> new SunatCatalogResponse.CatalogEntry(value.code(), value.description(), value.coreSupported()))
                        .toList(),
                Arrays.stream(SunatPriceType.values())
                        .map(value -> new SunatCatalogResponse.CatalogEntry(value.code(), value.description(), value.coreSupported()))
                        .toList(),
                Currency.getAvailableCurrencies().stream()
                        .sorted(Comparator.comparing(Currency::getCurrencyCode))
                        .map(value -> new SunatCatalogResponse.CatalogEntry(
                                value.getCurrencyCode(), value.getDisplayName(Locale.forLanguageTag("es-PE")), true))
                        .toList(),
                Arrays.stream(SunatUnitCode.values())
                        .map(value -> new SunatCatalogResponse.CatalogEntry(value.code(), value.description(), true))
                        .toList(),
                SunatNoteReasonCatalog.creditReasons().stream()
                        .map(value -> new SunatCatalogResponse.NoteReasonEntry(
                                value.code(), value.description(), true, value.allowedForReceipt()))
                        .toList(),
                SunatNoteReasonCatalog.debitReasons().stream()
                        .map(value -> new SunatCatalogResponse.NoteReasonEntry(
                                value.code(), value.description(), true, true))
                        .toList(),
                Arrays.stream(SunatAllowanceChargeReason.values())
                        .map(value -> new SunatCatalogResponse.AllowanceChargeEntry(
                                value.code(), value.description(), value.charge(), value.scope().name(),
                                value.affectsTaxBase(), value.coreSupported()))
                        .toList());
    }
}
