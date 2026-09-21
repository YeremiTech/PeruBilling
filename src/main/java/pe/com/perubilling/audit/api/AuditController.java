package pe.com.perubilling.audit.api;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import pe.com.perubilling.audit.infrastructure.AuditEventRepository;
import pe.com.perubilling.shared.api.PagedResponse;
import pe.com.perubilling.shared.security.TenantContext;

@RestController
@RequestMapping("/api/v1/audit")
@Tag(name = "Audit", description = "Eventos de auditoría del tenant autenticado")
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
public class AuditController {
    private final AuditEventRepository repository;
    private final TenantContext tenantContext;

    public AuditController(AuditEventRepository repository, TenantContext tenantContext) {
        this.repository = repository;
        this.tenantContext = tenantContext;
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Lista eventos de auditoría", description = "Devuelve una página estable del contrato API v1.")
    public PagedResponse<AuditEventResponse> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        int normalizedPage = Math.max(0, page);
        int normalizedSize = Math.min(100, Math.max(1, size));
        var result = repository.findAllByTenantIdOrderByCreatedAtDesc(
                tenantContext.requireTenantId(), PageRequest.of(normalizedPage, normalizedSize))
                .map(event -> new AuditEventResponse(
                        event.getId(), event.getActor(), event.getHttpMethod(), event.getRequestPath(),
                        event.getResponseStatus(), event.getRequestId(), event.getRemoteAddress(), event.getCreatedAt()));
        return PagedResponse.from(result);
    }
}
