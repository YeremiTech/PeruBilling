package pe.com.perubilling.voiding.api;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;

import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import pe.com.perubilling.voiding.application.VoidingService;

@RestController
@RequestMapping("/api/v1/documents")
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
public class VoidingController {
    private final VoidingService service;
    public VoidingController(VoidingService service){this.service=service;}

    @PostMapping("/{id}/void")
    @PreAuthorize("hasAnyRole('ADMIN','OPERATOR') or hasAuthority('SCOPE_DOCUMENT_WRITE')")
    public VoidingResponse voidDocument(@PathVariable UUID id,@Valid @RequestBody VoidDocumentRequest request){
        return service.requestVoid(id,request);
    }

    @PostMapping("/{id}/void/retry-unknown")
    @PreAuthorize("hasAnyRole('ADMIN','OPERATOR') or hasAuthority('SCOPE_DOCUMENT_WRITE')")
    public VoidingResponse retryUnknown(
            @PathVariable UUID id,
            @RequestParam(defaultValue = "false") boolean confirmRetry){
        return service.retryUnknownSubmission(id,confirmRetry);
    }
}
