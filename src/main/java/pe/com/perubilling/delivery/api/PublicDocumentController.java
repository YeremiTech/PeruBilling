package pe.com.perubilling.delivery.api;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;

import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import pe.com.perubilling.delivery.application.DocumentDeliveryService;
import pe.com.perubilling.shared.domain.DocumentPdfLayout;

@RestController
@RequestMapping("/public/v1/documents/{token}")
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
public class PublicDocumentController {
    private final DocumentDeliveryService service;
    public PublicDocumentController(DocumentDeliveryService service){this.service=service;}

    @GetMapping
    public ResponseEntity<PublicDocumentResponse> view(@PathVariable String token){
        return ResponseEntity.ok().headers(confidentialHeaders()).body(service.view(token));
    }

    @GetMapping("/pdf")
    public ResponseEntity<byte[]> pdf(
            @PathVariable String token,
            @RequestParam(defaultValue = "A4") DocumentPdfLayout layout) {
        return file(service.download(token, "pdf", layout));
    }

    @GetMapping("/xml")
    public ResponseEntity<byte[]> xml(@PathVariable String token){return file(service.download(token,"xml"));}

    private ResponseEntity<byte[]> file(DocumentDeliveryService.PublicArtifact artifact){
        HttpHeaders headers=confidentialHeaders();
        headers.setContentType(MediaType.parseMediaType(artifact.mediaType()));
        headers.set(HttpHeaders.CONTENT_DISPOSITION,"attachment; filename=\""+artifact.filename().replace("\"","")+"\"");
        return ResponseEntity.ok().headers(headers).body(artifact.content());
    }

    private HttpHeaders confidentialHeaders(){
        HttpHeaders headers=new HttpHeaders();
        headers.setCacheControl(CacheControl.noStore());
        headers.set("Pragma","no-cache");
        headers.set("Referrer-Policy","no-referrer");
        headers.set("X-Robots-Tag","noindex, nofollow, noarchive");
        return headers;
    }
}
