package pe.com.perubilling.billing.api;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pe.com.perubilling.billing.domain.ElectronicDocumentEntity;
import pe.com.perubilling.billing.infrastructure.ElectronicDocumentRepository;
import pe.com.perubilling.shared.domain.BusinessException;
import pe.com.perubilling.shared.security.TenantContext;
import pe.com.perubilling.shared.storage.ArtifactStorage;

@RestController
@RequestMapping("/api/v1/documents/{id}")
@Tag(name = "Document artifacts", description = "Descarga de XML, PDF y CDR del tenant autenticado")
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
public class DocumentArtifactController {
    private final ElectronicDocumentRepository documents;
    private final TenantContext tenantContext;
    private final ArtifactStorage storage;

    public DocumentArtifactController(
            ElectronicDocumentRepository documents,
            TenantContext tenantContext,
            ArtifactStorage storage) {
        this.documents = documents;
        this.tenantContext = tenantContext;
        this.storage = storage;
    }

    @GetMapping("/xml")
    @PreAuthorize("hasAnyRole('ADMIN','OPERATOR','VIEWER') or hasAuthority('SCOPE_DOCUMENT_READ')")
    @Operation(summary = "Descarga el XML firmado del comprobante")
    public ResponseEntity<byte[]> xml(@PathVariable UUID id) {
        var document = requireDocument(id);
        String path = document.getSignedXmlPath() != null
                ? document.getSignedXmlPath()
                : document.getXmlPath();
        return file(path, MediaType.APPLICATION_XML, document.getFullNumber() + ".xml");
    }

    @GetMapping("/pdf")
    @PreAuthorize("hasAnyRole('ADMIN','OPERATOR','VIEWER') or hasAuthority('SCOPE_DOCUMENT_READ')")
    @Operation(summary = "Descarga la representación PDF")
    public ResponseEntity<byte[]> pdf(@PathVariable UUID id) {
        var document = requireDocument(id);
        return file(document.getPdfPath(), MediaType.APPLICATION_PDF, document.getFullNumber() + ".pdf");
    }

    @GetMapping("/cdr")
    @PreAuthorize("hasAnyRole('ADMIN','OPERATOR','VIEWER') or hasAuthority('SCOPE_DOCUMENT_READ')")
    @Operation(summary = "Descarga el CDR recibido de SUNAT")
    public ResponseEntity<byte[]> cdr(@PathVariable UUID id) {
        var document = requireDocument(id);
        return file(
                document.getCdrPath(),
                MediaType.parseMediaType("application/zip"),
                "R-" + document.getFullNumber() + ".zip");
    }

    private ElectronicDocumentEntity requireDocument(UUID id) {
        return documents.findByIdAndTenantId(id, tenantContext.requireTenantId())
                .orElseThrow(() -> BusinessException.notFound(
                        "DOCUMENT_NOT_FOUND", "Documento no encontrado"));
    }

    private ResponseEntity<byte[]> file(String path, MediaType type, String name) {
        if (path == null) {
            throw BusinessException.notFound(
                    "ARTIFACT_NOT_READY", "El artefacto todavía no está disponible");
        }
        String safeName = name.replace("\"", "");
        return ResponseEntity.ok()
                .contentType(type)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + safeName + "\"")
                .body(storage.read(path));
    }
}
