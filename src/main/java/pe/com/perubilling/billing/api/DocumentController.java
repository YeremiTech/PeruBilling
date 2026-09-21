package pe.com.perubilling.billing.api;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import pe.com.perubilling.billing.application.DocumentService;
import pe.com.perubilling.billing.domain.DocumentStatus;
import pe.com.perubilling.shared.domain.BusinessException;
import pe.com.perubilling.shared.domain.DocumentType;
import pe.com.perubilling.shared.security.ApiKeyPrincipal;
import pe.com.perubilling.shared.api.PagedResponse;

@RestController
@RequestMapping("/api/v1")
@Tag(name = "Documents", description = "Emisión y consulta de comprobantes electrónicos")
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
public class DocumentController {
    private final DocumentService service;
    public DocumentController(DocumentService service) { this.service = service; }

    @PostMapping("/invoices")
    @Operation(summary = "Emite una factura", description = "Para integraciones mediante API Key, Idempotency-Key es obligatoria.")
    @PreAuthorize("hasAnyRole('ADMIN','OPERATOR') or hasAuthority('SCOPE_DOCUMENT_WRITE')")
    public ResponseEntity<DocumentResponse> createInvoice(@Valid @RequestBody CreateDocumentRequest request,
            @Parameter(description = "Clave idempotente del sistema externo")
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            Authentication authentication) {
        return created(service.create(DocumentType.INVOICE, request, requireM2mIdempotency(authentication, idempotencyKey)));
    }

    @PostMapping("/receipts")
    @Operation(summary = "Emite una boleta", description = "Para integraciones mediante API Key, Idempotency-Key es obligatoria.")
    @PreAuthorize("hasAnyRole('ADMIN','OPERATOR') or hasAuthority('SCOPE_DOCUMENT_WRITE')")
    public ResponseEntity<DocumentResponse> createReceipt(@Valid @RequestBody CreateDocumentRequest request,
            @Parameter(description = "Clave idempotente del sistema externo")
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            Authentication authentication) {
        return created(service.create(DocumentType.RECEIPT, request, requireM2mIdempotency(authentication, idempotencyKey)));
    }

    @PostMapping("/credit-notes")
    @Operation(summary = "Emite una nota de crédito")
    @PreAuthorize("hasAnyRole('ADMIN','OPERATOR') or hasAuthority('SCOPE_DOCUMENT_WRITE')")
    public ResponseEntity<DocumentResponse> createCreditNote(@Valid @RequestBody CreateDocumentRequest request,
            @Parameter(description = "Clave idempotente del sistema externo")
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            Authentication authentication) {
        return created(service.create(DocumentType.CREDIT_NOTE, request, requireM2mIdempotency(authentication, idempotencyKey)));
    }

    @PostMapping("/debit-notes")
    @Operation(summary = "Emite una nota de débito")
    @PreAuthorize("hasAnyRole('ADMIN','OPERATOR') or hasAuthority('SCOPE_DOCUMENT_WRITE')")
    public ResponseEntity<DocumentResponse> createDebitNote(@Valid @RequestBody CreateDocumentRequest request,
            @Parameter(description = "Clave idempotente del sistema externo")
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            Authentication authentication) {
        return created(service.create(DocumentType.DEBIT_NOTE, request, requireM2mIdempotency(authentication, idempotencyKey)));
    }

    @GetMapping("/documents/{id}")
    @Operation(summary = "Obtiene un comprobante por ID")
    @PreAuthorize("hasAnyRole('ADMIN','OPERATOR','VIEWER') or hasAuthority('SCOPE_DOCUMENT_READ')")
    public DocumentResponse get(@PathVariable UUID id) { return service.get(id); }

    @GetMapping("/documents")
    @Operation(summary = "Busca comprobantes", description = "Retorna PagedResponse v1 para mantener un contrato independiente de Spring Data.")
    @PreAuthorize("hasAnyRole('ADMIN','OPERATOR','VIEWER') or hasAuthority('SCOPE_DOCUMENT_READ')")
    public PagedResponse<DocumentResponse> list(
            @RequestParam(required = false) UUID issuerId,
            @RequestParam(required = false) DocumentType documentType,
            @RequestParam(required = false) String operationType,
            @RequestParam(required = false) String series,
            @RequestParam(required = false) String number,
            @RequestParam(required = false) String externalId,
            @RequestParam(required = false) DocumentStatus status,
            @RequestParam(required = false) String customerDocumentNumber,
            @RequestParam(required = false) LocalDate fromIssueDate,
            @RequestParam(required = false) LocalDate toIssueDate,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return PagedResponse.from(service.list(new DocumentSearchCriteria(
                issuerId, documentType, operationType, series, number, externalId, status,
                customerDocumentNumber, fromIssueDate, toIssueDate), page, size));
    }

    @PostMapping("/documents/{id}/reconcile")
    @Operation(summary = "Solicita reconciliación SUNAT")
    @PreAuthorize("hasAnyRole('ADMIN','OPERATOR') or hasAuthority('SCOPE_DOCUMENT_WRITE')")
    public DocumentResponse reconcile(@PathVariable UUID id) { return service.requestReconciliation(id); }

    @GetMapping("/documents/{id}/history")
    @Operation(summary = "Consulta el historial de estados del comprobante")
    @PreAuthorize("hasAnyRole('ADMIN','OPERATOR','VIEWER') or hasAuthority('SCOPE_DOCUMENT_READ')")
    public List<DocumentHistoryResponse> history(@PathVariable UUID id) { return service.history(id); }

    private String requireM2mIdempotency(Authentication authentication, String idempotencyKey) {
        if (authentication != null && authentication.getPrincipal() instanceof ApiKeyPrincipal
                && (idempotencyKey == null || idempotencyKey.isBlank())) {
            throw BusinessException.badRequest("IDEMPOTENCY_KEY_REQUIRED",
                    "Idempotency-Key es obligatoria para emisión mediante API Key");
        }
        return idempotencyKey;
    }

    private ResponseEntity<DocumentResponse> created(DocumentResponse response) {
        return ResponseEntity.created(URI.create("/api/v1/documents/" + response.id())).body(response);
    }
}
