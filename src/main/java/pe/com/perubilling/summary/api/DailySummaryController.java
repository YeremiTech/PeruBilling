package pe.com.perubilling.summary.api;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import pe.com.perubilling.shared.api.PagedResponse;
import pe.com.perubilling.summary.application.DailySummaryService;

@RestController
@RequestMapping("/api/v1/daily-summaries")
@Tag(name = "Daily summaries", description = "Resumen Diario de boletas y notas asociadas")
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
public class DailySummaryController {
    private final DailySummaryService service;

    public DailySummaryController(DailySummaryService service) {
        this.service = service;
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','OPERATOR') or hasAuthority('SCOPE_DOCUMENT_WRITE')")
    @Operation(summary = "Genera un Resumen Diario")
    public ResponseEntity<DailySummaryResponse> create(
            @RequestParam UUID issuerId,
            @RequestParam(required = false) LocalDate referenceDate) {
        return ResponseEntity.accepted().body(service.create(issuerId, referenceDate));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','OPERATOR','VIEWER') or hasAuthority('SCOPE_DOCUMENT_READ')")
    @Operation(summary = "Obtiene un Resumen Diario por ID")
    public DailySummaryResponse get(@PathVariable UUID id) {
        return service.get(id);
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','OPERATOR','VIEWER') or hasAuthority('SCOPE_DOCUMENT_READ')")
    @Operation(summary = "Lista Resúmenes Diarios")
    public PagedResponse<DailySummaryResponse> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return PagedResponse.from(service.list(page, size));
    }
}
