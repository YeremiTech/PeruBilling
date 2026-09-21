package pe.com.perubilling.sunat.infrastructure;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import javax.xml.parsers.DocumentBuilderFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.w3c.dom.Document;
import org.w3c.dom.NodeList;
import pe.com.perubilling.shared.xml.SecureXml;
import pe.com.perubilling.sunat.domain.SunatClientException;

@Component
public class SunatSoapClient {
    private final HttpClient client;
    private final Duration requestTimeout;

    public SunatSoapClient(@Value("${app.sunat.connect-timeout-seconds:10}") long connectTimeout,
                           @Value("${app.sunat.request-timeout-seconds:30}") long requestTimeout) {
        this.client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(connectTimeout)).build();
        this.requestTimeout = Duration.ofSeconds(requestTimeout);
    }

    public SoapBillResponse sendBill(String endpoint, String username, String password, String fileName, byte[] zip) {
        String envelope = envelope(username, password, fileName, Base64.getEncoder().encodeToString(zip));
        HttpRequest request = HttpRequest.newBuilder(URI.create(endpoint))
                .timeout(requestTimeout)
                .header("Content-Type", "text/xml; charset=utf-8")
                .header("SOAPAction", "")
                .POST(HttpRequest.BodyPublishers.ofString(envelope, StandardCharsets.UTF_8))
                .build();
        try {
            HttpResponse<byte[]> response = client.send(request, HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() >= 500) {
                throw new SunatClientException("SUNAT_HTTP_" + response.statusCode(), "SUNAT respondió HTTP " + response.statusCode(), true);
            }
            Document doc = SecureXml.parse(response.body());
            String fault = first(doc, "faultstring");
            if (fault != null) {
                String faultCode = first(doc, "faultcode");
                throw new SunatClientException(faultCode == null ? "SUNAT_SOAP_FAULT" : faultCode, fault, isRetryableFault(faultCode, fault));
            }
            String applicationResponse = first(doc, "applicationResponse");
            if (applicationResponse == null || applicationResponse.isBlank()) {
                throw new SunatClientException("SUNAT_EMPTY_RESPONSE", "SUNAT no devolvió applicationResponse", true);
            }
            return new SoapBillResponse(Base64.getDecoder().decode(applicationResponse.trim()), response.statusCode());
        } catch (SunatClientException ex) {
            throw ex;
        } catch (java.net.http.HttpTimeoutException ex) {
            throw new SunatClientException("SUNAT_TIMEOUT", "Tiempo de espera agotado al comunicarse con SUNAT", true, ex);
        } catch (java.io.IOException ex) {
            throw new SunatClientException("SUNAT_IO", "Error de red al comunicarse con SUNAT", true, ex);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new SunatClientException("SUNAT_INTERRUPTED", "Comunicación SUNAT interrumpida", true, ex);
        } catch (Exception ex) {
            throw new SunatClientException("SUNAT_PROTOCOL", "Respuesta SOAP SUNAT inválida", false, ex);
        }
    }

    public SoapSummaryResponse sendSummary(String endpoint, String username, String password, String fileName, byte[] zip) {
        String body = """
                <ser:sendSummary>
                  <fileName>%s</fileName>
                  <contentFile>%s</contentFile>
                </ser:sendSummary>
                """.formatted(xml(fileName), Base64.getEncoder().encodeToString(zip));
        SoapRaw raw = invoke(endpoint, username, password, body);
        String ticket = first(raw.document(), "ticket");
        if (ticket == null || ticket.isBlank()) throw new SunatClientException("SUNAT_EMPTY_TICKET", "SUNAT no devolvió ticket para el resumen", true);
        return new SoapSummaryResponse(ticket.trim(), raw.httpStatus());
    }

    public SoapStatusResponse getStatus(String endpoint, String username, String password, String ticket) {
        String body = """
                <ser:getStatus>
                  <ticket>%s</ticket>
                </ser:getStatus>
                """.formatted(xml(ticket));
        SoapRaw raw = invoke(endpoint, username, password, body);
        String statusCode = first(raw.document(), "statusCode");
        String content = first(raw.document(), "content");
        byte[] cdr = content == null || content.isBlank() ? null : Base64.getDecoder().decode(content.trim());
        return new SoapStatusResponse(statusCode == null ? "UNKNOWN" : statusCode.trim(), cdr, raw.httpStatus());
    }


    public SoapCdrStatusResponse getStatusCdr(
            String endpoint, String username, String password, String ruc,
            String documentType, String series, long number) {
        String body = """
                <ser:getStatusCdr>
                  <rucComprobante>%s</rucComprobante>
                  <tipoComprobante>%s</tipoComprobante>
                  <serieComprobante>%s</serieComprobante>
                  <numeroComprobante>%d</numeroComprobante>
                </ser:getStatusCdr>
                """.formatted(xml(ruc), xml(documentType), xml(series), number);
        SoapRaw raw = invoke(endpoint, username, password, body);
        String statusCode = first(raw.document(), "statusCode");
        String statusMessage = first(raw.document(), "statusMessage");
        String content = first(raw.document(), "content");
        byte[] cdr = content == null || content.isBlank() ? null : Base64.getDecoder().decode(content.trim());
        return new SoapCdrStatusResponse(
                statusCode == null ? "UNKNOWN" : statusCode.trim(),
                statusMessage == null ? "" : statusMessage.trim(),
                cdr,
                raw.httpStatus());
    }

    private SoapRaw invoke(String endpoint, String username, String password, String body) {
        String envelope = """
                <?xml version="1.0" encoding="UTF-8"?>
                <soapenv:Envelope xmlns:soapenv="http://schemas.xmlsoap.org/soap/envelope/"
                                  xmlns:ser="http://service.sunat.gob.pe"
                                  xmlns:wsse="http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-wssecurity-secext-1.0.xsd">
                  <soapenv:Header>
                    <wsse:Security>
                      <wsse:UsernameToken>
                        <wsse:Username>%s</wsse:Username>
                        <wsse:Password>%s</wsse:Password>
                      </wsse:UsernameToken>
                    </wsse:Security>
                  </soapenv:Header>
                  <soapenv:Body>%s</soapenv:Body>
                </soapenv:Envelope>
                """.formatted(xml(username), xml(password), body);
        HttpRequest request = HttpRequest.newBuilder(URI.create(endpoint)).timeout(requestTimeout)
                .header("Content-Type", "text/xml; charset=utf-8").header("SOAPAction", "")
                .POST(HttpRequest.BodyPublishers.ofString(envelope, StandardCharsets.UTF_8)).build();
        try {
            HttpResponse<byte[]> response = client.send(request, HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() >= 500) throw new SunatClientException("SUNAT_HTTP_" + response.statusCode(), "SUNAT respondió HTTP " + response.statusCode(), true);
            Document doc = SecureXml.parse(response.body());
            String fault = first(doc, "faultstring");
            if (fault != null) {
                String faultCode = first(doc, "faultcode");
                throw new SunatClientException(faultCode == null ? "SUNAT_SOAP_FAULT" : faultCode, fault, isRetryableFault(faultCode, fault));
            }
            return new SoapRaw(doc, response.statusCode());
        } catch (SunatClientException ex) { throw ex;
        } catch (java.net.http.HttpTimeoutException ex) { throw new SunatClientException("SUNAT_TIMEOUT", "Tiempo de espera agotado al comunicarse con SUNAT", true, ex);
        } catch (java.io.IOException ex) { throw new SunatClientException("SUNAT_IO", "Error de red al comunicarse con SUNAT", true, ex);
        } catch (InterruptedException ex) { Thread.currentThread().interrupt(); throw new SunatClientException("SUNAT_INTERRUPTED", "Comunicación SUNAT interrumpida", true, ex);
        } catch (Exception ex) { throw new SunatClientException("SUNAT_PROTOCOL", "Respuesta SOAP SUNAT inválida", false, ex); }
    }

    private boolean isRetryableFault(String code, String message) {
        String text = ((code == null ? "" : code) + " " + (message == null ? "" : message)).toLowerCase();
        return text.contains("server") || text.contains("timeout") || text.contains("temporal") || text.contains("disponib");
    }

    private String first(Document doc, String localName) {
        NodeList nodes = doc.getElementsByTagNameNS("*", localName);
        if (nodes.getLength() == 0) nodes = doc.getElementsByTagName(localName);
        return nodes.getLength() == 0 ? null : nodes.item(0).getTextContent().trim();
    }

    private String envelope(String username, String password, String fileName, String content) {
        return """
                <?xml version="1.0" encoding="UTF-8"?>
                <soapenv:Envelope xmlns:soapenv="http://schemas.xmlsoap.org/soap/envelope/"
                                  xmlns:ser="http://service.sunat.gob.pe"
                                  xmlns:wsse="http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-wssecurity-secext-1.0.xsd">
                  <soapenv:Header>
                    <wsse:Security>
                      <wsse:UsernameToken>
                        <wsse:Username>%s</wsse:Username>
                        <wsse:Password>%s</wsse:Password>
                      </wsse:UsernameToken>
                    </wsse:Security>
                  </soapenv:Header>
                  <soapenv:Body>
                    <ser:sendBill>
                      <fileName>%s</fileName>
                      <contentFile>%s</contentFile>
                    </ser:sendBill>
                  </soapenv:Body>
                </soapenv:Envelope>
                """.formatted(xml(username), xml(password), xml(fileName), content);
    }

    private String xml(String value) {
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&apos;");
    }

    public record SoapBillResponse(byte[] cdrZip, int httpStatus) {}
    public record SoapSummaryResponse(String ticket, int httpStatus) {}
    public record SoapStatusResponse(String statusCode, byte[] cdrZip, int httpStatus) {}
    public record SoapCdrStatusResponse(String statusCode, String statusMessage, byte[] cdrZip, int httpStatus) {}
    private record SoapRaw(Document document, int httpStatus) {}
}
