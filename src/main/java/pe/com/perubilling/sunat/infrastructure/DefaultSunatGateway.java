package pe.com.perubilling.sunat.infrastructure;

import java.nio.charset.StandardCharsets;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import pe.com.perubilling.cpe.domain.DocumentBundle;
import pe.com.perubilling.issuer.domain.SunatEnvironment;
import pe.com.perubilling.shared.crypto.SecretCryptoService;
import pe.com.perubilling.sunat.application.SunatGateway;
import pe.com.perubilling.sunat.domain.SunatClientException;
import pe.com.perubilling.sunat.domain.SunatReconciliationResult;
import pe.com.perubilling.sunat.domain.SunatReconciliationStatus;
import pe.com.perubilling.sunat.domain.SunatSubmissionResult;

@Component
public class DefaultSunatGateway implements SunatGateway {
    private final SunatSoapClient soapClient;
    private final CdrParser cdrParser;
    private final SecretCryptoService crypto;
    private final String betaUrl;
    private final String productionUrl;
    private final String consultUrl;
    private final SunatEndpointPolicy endpointPolicy;

    public DefaultSunatGateway(
            SunatSoapClient soapClient,
            CdrParser cdrParser,
            SecretCryptoService crypto,
            @Value("${app.sunat.beta-url}") String betaUrl,
            @Value("${app.sunat.production-url}") String productionUrl,
            @Value("${app.sunat.consult-url}") String consultUrl,
            SunatEndpointPolicy endpointPolicy) {
        this.soapClient = soapClient;
        this.cdrParser = cdrParser;
        this.crypto = crypto;
        this.betaUrl = betaUrl;
        this.productionUrl = productionUrl;
        this.consultUrl = consultUrl;
        this.endpointPolicy = endpointPolicy;
    }

    @Override
    public SunatSubmissionResult submitBill(DocumentBundle bundle, byte[] signedXml) {
        var issuer = bundle.issuer();
        var document = bundle.document();
        if (issuer.getSunatEnvironment() == SunatEnvironment.LOCAL) {
            byte[] cdr = LocalCdrFactory.accepted(issuer.getRuc(), document.getDocumentType().getSunatCode(), document.getFullNumber());
            return cdrParser.parse(cdr, 200);
        }
        Credentials credentials = credentials(bundle);
        String baseName = issuer.getRuc() + "-" + document.getDocumentType().getSunatCode() + "-" + document.getFullNumber();
        byte[] zip = ZipSupport.zipSingle(baseName + ".xml", signedXml);
        String endpoint = endpointPolicy.validate(
                issuer.getSunatEnvironment(),
                issuer.getSunatEnvironment() == SunatEnvironment.BETA ? betaUrl : productionUrl);
        SunatSoapClient.SoapBillResponse response = soapClient.sendBill(
                endpoint, credentials.username(), credentials.password(), baseName + ".zip", zip);
        return cdrParser.parse(response.cdrZip(), response.httpStatus());
    }

    @Override
    public SunatReconciliationResult recoverCdr(DocumentBundle bundle) {
        var issuer = bundle.issuer();
        var document = bundle.document();
        if (issuer.getSunatEnvironment() != SunatEnvironment.PRODUCTION) {
            return SunatReconciliationResult.of(
                    SunatReconciliationStatus.UNKNOWN,
                    "La consulta getStatusCdr solo está habilitada para producción");
        }

        Credentials credentials = credentials(bundle);
        String endpoint = endpointPolicy.validate(SunatEnvironment.PRODUCTION, consultUrl);
        var response = soapClient.getStatusCdr(
                endpoint, credentials.username(), credentials.password(), issuer.getRuc(),
                document.getDocumentType().getSunatCode(), document.getSeries(), document.getCorrelativo());

        if ("0011".equals(response.statusCode())) {
            return SunatReconciliationResult.of(
                    SunatReconciliationStatus.CONFIRMED_NOT_FOUND,
                    response.statusMessage() == null
                            ? "SUNAT confirma que el comprobante no existe"
                            : response.statusMessage());
        }

        if (response.cdrZip() != null && response.cdrZip().length > 0) {
            SunatSubmissionResult parsed = cdrParser.parse(response.cdrZip(), response.httpStatus());
            SunatReconciliationStatus status = parsed.accepted()
                    ? SunatReconciliationStatus.FOUND_ACCEPTED
                    : SunatReconciliationStatus.FOUND_REJECTED;
            return SunatReconciliationResult.found(status, parsed);
        }

        if ("0001".equals(response.statusCode())) {
            return SunatReconciliationResult.found(
                    SunatReconciliationStatus.FOUND_ACCEPTED,
                    new SunatSubmissionResult(
                            "0", response.statusMessage(), java.util.List.of(), null, response.httpStatus()));
        }
        if ("0002".equals(response.statusCode())) {
            return SunatReconciliationResult.found(
                    SunatReconciliationStatus.FOUND_REJECTED,
                    new SunatSubmissionResult(
                            "SUNAT_STATUS_0002", response.statusMessage(), java.util.List.of(), null, response.httpStatus()));
        }
        if ("0003".equals(response.statusCode())) {
            return SunatReconciliationResult.found(
                    SunatReconciliationStatus.FOUND_VOIDED,
                    new SunatSubmissionResult(
                            "SUNAT_STATUS_0003", response.statusMessage(), java.util.List.of(), null, response.httpStatus()));
        }

        return SunatReconciliationResult.of(
                SunatReconciliationStatus.UNKNOWN,
                response.statusMessage() == null
                        ? "SUNAT no devolvió un estado concluyente"
                        : response.statusMessage());
    }

    private Credentials credentials(DocumentBundle bundle) {
        var issuer = bundle.issuer();
        if (issuer.getSolUser() == null || issuer.getSolPasswordEncrypted() == null) {
            throw new SunatClientException(
                    "SUNAT_CREDENTIALS_MISSING",
                    "El emisor no tiene credenciales SOL configuradas",
                    false);
        }
        return new Credentials(issuer.getRuc() + issuer.getSolUser(), crypto.decrypt(issuer.getSolPasswordEncrypted()));
    }

    private record Credentials(String username, String password) {}

    private static final class LocalCdrFactory {
        static byte[] accepted(String ruc, String type, String number) {
            String xml = """
                    <?xml version="1.0" encoding="UTF-8"?>
                    <ApplicationResponse xmlns="urn:oasis:names:specification:ubl:schema:xsd:ApplicationResponse-2"
                      xmlns:cbc="urn:oasis:names:specification:ubl:schema:xsd:CommonBasicComponents-2"
                      xmlns:cac="urn:oasis:names:specification:ubl:schema:xsd:CommonAggregateComponents-2">
                      <cbc:ID>R-%s-%s-%s</cbc:ID>
                      <cac:DocumentResponse>
                        <cac:Response><cbc:ResponseCode>0</cbc:ResponseCode><cbc:Description>Aceptado en modo LOCAL</cbc:Description></cac:Response>
                      </cac:DocumentResponse>
                    </ApplicationResponse>
                    """.formatted(ruc, type, number);
            return ZipSupport.zipSingle(
                    "R-" + ruc + "-" + type + "-" + number + ".xml",
                    xml.getBytes(StandardCharsets.UTF_8));
        }
    }
}
