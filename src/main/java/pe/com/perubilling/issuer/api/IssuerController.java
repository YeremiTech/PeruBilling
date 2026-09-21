package pe.com.perubilling.issuer.api;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;

import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import pe.com.perubilling.issuer.application.IssuerService;

@RestController
@RequestMapping("/api/v1/issuers")
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
public class IssuerController {
    private final IssuerService service;
    public IssuerController(IssuerService service) { this.service = service; }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<IssuerResponse> create(@Valid @RequestBody CreateIssuerRequest request) {
        var response = service.create(request);
        return ResponseEntity.created(URI.create("/api/v1/issuers/" + response.id())).body(response);
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','OPERATOR','VIEWER') or hasAuthority('SCOPE_ISSUER_READ')")
    public List<IssuerResponse> list() { return service.list(); }

    @GetMapping("/{issuerId}")
    @PreAuthorize("hasAnyRole('ADMIN','OPERATOR','VIEWER') or hasAuthority('SCOPE_ISSUER_READ')")
    public IssuerResponse get(@PathVariable UUID issuerId) { return service.get(issuerId); }

    @PatchMapping("/{issuerId}/activation")
    @PreAuthorize("hasRole('ADMIN')")
    public IssuerResponse setActive(@PathVariable UUID issuerId, @RequestParam boolean active) {
        return service.setActive(issuerId, active);
    }

    @PutMapping("/{issuerId}/sunat-credentials")
    @PreAuthorize("hasRole('ADMIN')")
    public IssuerResponse configureSunat(@PathVariable UUID issuerId, @Valid @RequestBody SunatCredentialsRequest request) {
        return service.configureSunat(issuerId, request);
    }

    @PostMapping("/{issuerId}/series")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<SeriesResponse> createSeries(@PathVariable UUID issuerId, @Valid @RequestBody CreateSeriesRequest request) {
        var response = service.createSeries(issuerId, request);
        return ResponseEntity.created(URI.create("/api/v1/issuers/" + issuerId + "/series/" + response.id())).body(response);
    }

    @GetMapping("/{issuerId}/series")
    @PreAuthorize("hasAnyRole('ADMIN','OPERATOR','VIEWER') or hasAuthority('SCOPE_ISSUER_READ')")
    public List<SeriesResponse> listSeries(@PathVariable UUID issuerId) { return service.listSeries(issuerId); }

    @PatchMapping("/{issuerId}/series/{seriesId}/activation")
    @PreAuthorize("hasRole('ADMIN')")
    public SeriesResponse setSeriesActive(@PathVariable UUID issuerId, @PathVariable UUID seriesId, @RequestParam boolean active) {
        return service.setSeriesActive(issuerId, seriesId, active);
    }

    @PostMapping(value = "/{issuerId}/certificates", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('ADMIN')")
    public CertificateResponse uploadCertificate(@PathVariable UUID issuerId,
                                                  @RequestPart("file") MultipartFile file,
                                                  @RequestParam("password") String password,
                                                  @RequestParam(value = "alias", required = false) String alias) {
        return service.uploadCertificate(issuerId, file, password, alias);
    }

    @GetMapping("/{issuerId}/certificates")
    @PreAuthorize("hasRole('ADMIN')")
    public List<CertificateResponse> listCertificates(@PathVariable UUID issuerId) { return service.listCertificates(issuerId); }

    @PatchMapping("/{issuerId}/certificates/{certificateId}/activate")
    @PreAuthorize("hasRole('ADMIN')")
    public CertificateResponse activateCertificate(@PathVariable UUID issuerId, @PathVariable UUID certificateId) {
        return service.activateCertificate(issuerId, certificateId);
    }

    @PatchMapping("/{issuerId}/certificates/{certificateId}/revoke")
    @PreAuthorize("hasRole('ADMIN')")
    public CertificateResponse revokeCertificate(@PathVariable UUID issuerId, @PathVariable UUID certificateId) {
        return service.revokeCertificate(issuerId, certificateId);
    }
}
