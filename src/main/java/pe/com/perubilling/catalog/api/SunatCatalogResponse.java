package pe.com.perubilling.catalog.api;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "Catálogos y capacidades regulatorias conocidas por la versión activa del motor")
public record SunatCatalogResponse(
        String rulesVersion,
        String coverage,
        List<CatalogEntry> documentTypes,
        List<CatalogEntry> identityDocumentTypes,
        List<CatalogEntry> operationTypes,
        List<CatalogEntry> taxAffectations,
        List<CatalogEntry> taxSchemes,
        List<CatalogEntry> priceTypes,
        List<CatalogEntry> currencies,
        List<CatalogEntry> unitCodes,
        List<NoteReasonEntry> creditNoteReasons,
        List<NoteReasonEntry> debitNoteReasons,
        List<AllowanceChargeEntry> allowanceChargeReasons
) {
    public record CatalogEntry(String code, String description, boolean supported) {}
    public record NoteReasonEntry(String code, String description, boolean supportedForInvoice, boolean supportedForReceipt) {}
    public record AllowanceChargeEntry(
            String code,
            String description,
            boolean charge,
            String scope,
            boolean affectsTaxBase,
            boolean supported) {}
}
