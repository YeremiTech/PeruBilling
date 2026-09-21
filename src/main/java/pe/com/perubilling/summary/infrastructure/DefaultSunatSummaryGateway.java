package pe.com.perubilling.summary.infrastructure;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import pe.com.perubilling.issuer.domain.IssuerEntity;
import pe.com.perubilling.issuer.domain.SunatEnvironment;
import pe.com.perubilling.shared.crypto.SecretCryptoService;
import pe.com.perubilling.summary.application.SunatSummaryGateway;
import pe.com.perubilling.sunat.domain.SunatClientException;
import pe.com.perubilling.sunat.infrastructure.CdrParser;
import pe.com.perubilling.sunat.infrastructure.SunatEndpointPolicy;
import pe.com.perubilling.sunat.infrastructure.SunatSoapClient;

@Component
public class DefaultSunatSummaryGateway implements SunatSummaryGateway {
    private final SunatSoapClient soap;
    private final SecretCryptoService crypto;
    private final CdrParser cdrParser;
    private final String betaUrl;
    private final String productionUrl;
    private final SunatEndpointPolicy endpointPolicy;

    public DefaultSunatSummaryGateway(
            SunatSoapClient soap,
            SecretCryptoService crypto,
            CdrParser cdrParser,
            @Value("${app.sunat.beta-url}") String betaUrl,
            @Value("${app.sunat.production-url}") String productionUrl,
            SunatEndpointPolicy endpointPolicy) {
        this.soap = soap;
        this.crypto = crypto;
        this.cdrParser = cdrParser;
        this.betaUrl = betaUrl;
        this.productionUrl = productionUrl;
        this.endpointPolicy = endpointPolicy;
    }

    @Override
    public SummaryTicket submit(IssuerEntity issuer, String baseName, byte[] xml) {
        if (issuer.getSunatEnvironment() == SunatEnvironment.LOCAL) {
            return new SummaryTicket("LOCAL-" + baseName, 200);
        }
        var credentials = credentials(issuer);
        byte[] zip = zip(baseName + ".xml", xml);
        var response = soap.sendSummary(
                endpoint(issuer),
                credentials.user(),
                credentials.password(),
                baseName + ".zip",
                zip);
        return new SummaryTicket(response.ticket(), response.httpStatus());
    }

    @Override
    public TicketResult getStatus(IssuerEntity issuer, String ticket) {
        if (issuer.getSunatEnvironment() == SunatEnvironment.LOCAL) {
            byte[] cdr = localCdr(issuer.getRuc(), ticket);
            return new TicketResult("0", "Resumen aceptado en modo LOCAL", cdr, 200);
        }

        var credentials = credentials(issuer);
        var response = soap.getStatus(
                endpoint(issuer), credentials.user(), credentials.password(), ticket);
        if ("98".equals(response.statusCode())) {
            return new TicketResult(
                    "98", "SUNAT continúa procesando el ticket", null, response.httpStatus());
        }
        if (response.cdrZip() != null) {
            var parsed = cdrParser.parse(response.cdrZip(), response.httpStatus());
            return new TicketResult(
                    parsed.responseCode(),
                    parsed.description(),
                    response.cdrZip(),
                    response.httpStatus());
        }
        return new TicketResult(
                response.statusCode(),
                "SUNAT finalizó el ticket sin CDR",
                null,
                response.httpStatus());
    }

    private Credentials credentials(IssuerEntity issuer) {
        if (issuer.getSolUser() == null || issuer.getSolPasswordEncrypted() == null) {
            throw new SunatClientException(
                    "SUNAT_CREDENTIALS_MISSING", "El emisor no tiene credenciales SOL", false);
        }
        return new Credentials(
                issuer.getRuc() + issuer.getSolUser(),
                crypto.decrypt(issuer.getSolPasswordEncrypted()));
    }

    private String endpoint(IssuerEntity issuer) {
        String configured = issuer.getSunatEnvironment() == SunatEnvironment.BETA
                ? betaUrl
                : productionUrl;
        return endpointPolicy.validate(issuer.getSunatEnvironment(), configured);
    }

    private byte[] zip(String name, byte[] content) {
        try {
            var output = new ByteArrayOutputStream();
            try (var zip = new ZipOutputStream(output)) {
                zip.putNextEntry(new ZipEntry(name));
                zip.write(content);
                zip.closeEntry();
            }
            return output.toByteArray();
        } catch (Exception ex) {
            throw new IllegalStateException("No se pudo crear el ZIP del Resumen Diario", ex);
        }
    }

    private byte[] localCdr(String ruc, String ticket) {
        String xml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <ApplicationResponse xmlns="urn:oasis:names:specification:ubl:schema:xsd:ApplicationResponse-2"
                                     xmlns:cbc="urn:oasis:names:specification:ubl:schema:xsd:CommonBasicComponents-2"
                                     xmlns:cac="urn:oasis:names:specification:ubl:schema:xsd:CommonAggregateComponents-2">
                  <cbc:ID>%s</cbc:ID>
                  <cac:DocumentResponse>
                    <cac:Response>
                      <cbc:ResponseCode>0</cbc:ResponseCode>
                      <cbc:Description>Resumen aceptado en modo LOCAL</cbc:Description>
                    </cac:Response>
                  </cac:DocumentResponse>
                </ApplicationResponse>
                """.formatted(ticket);
        return zip("R-" + ruc + "-" + ticket + ".xml", xml.getBytes(StandardCharsets.UTF_8));
    }

    private record Credentials(String user, String password) {}
}
