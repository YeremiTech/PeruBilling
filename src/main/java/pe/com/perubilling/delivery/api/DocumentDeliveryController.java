package pe.com.perubilling.delivery.api;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;

import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pe.com.perubilling.delivery.application.DocumentDeliveryService;

@RestController
@RequestMapping("/api/v1/documents/{id}")
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
public class DocumentDeliveryController {
    private final DocumentDeliveryService service;
    public DocumentDeliveryController(DocumentDeliveryService service){this.service=service;}

    @PostMapping("/access-links")
    @PreAuthorize("hasAnyRole('ADMIN','OPERATOR') or hasAuthority('SCOPE_DOCUMENT_WRITE')")
    public DocumentAccessLinkResponse createLink(@PathVariable UUID id,
            @Valid @RequestBody(required=false) CreateAccessLinkRequest request) {
        return service.createAccessLink(id, request==null?null:request.validDays());
    }

    @PostMapping("/grant")
    @PreAuthorize("hasAnyRole('ADMIN','OPERATOR') or hasAuthority('SCOPE_DOCUMENT_WRITE')")
    public void grant(@PathVariable UUID id,@Valid @RequestBody MarkGrantedRequest request) {
        service.markGranted(id,request.channel());
    }
}
