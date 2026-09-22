package pe.com.perubilling.shared.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.bind.MissingServletRequestParameterException;
import pe.com.perubilling.shared.domain.BusinessException;

class GlobalExceptionHandlerTest {
    private final ApiErrorFactory factory = new ApiErrorFactory();
    private final GlobalExceptionHandler handler = new GlobalExceptionHandler(factory);

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @Test
    void preservesBusinessErrorContract() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/issuers/missing");
        MDC.put("traceId", "request-123");

        var response = handler.handleBusiness(
                BusinessException.notFound("ISSUER_NOT_FOUND", "Emisor no encontrado"), request);

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("ISSUER_NOT_FOUND", response.getBody().code());
        assertEquals("request-123", response.getBody().requestId());
    }

    @Test
    void mapsSeriesUniqueConstraintToDomainConflict() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/issuers/id/series");
        var exception = new DataIntegrityViolationException(
                "duplicate key value violates unique constraint \"uq_document_series\"");

        var response = handler.handleIntegrity(exception, request);

        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("SERIES_ALREADY_EXISTS", response.getBody().code());
    }

    @Test
    void mapsCertificateUniqueConstraintToDomainConflict() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/issuers/id/certificates");
        var exception = new DataIntegrityViolationException(
                "duplicate key value violates unique constraint \"uq_certificate_issuer_fingerprint\"");

        var response = handler.handleIntegrity(exception, request);

        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("CERTIFICATE_ALREADY_EXISTS", response.getBody().code());
    }


    @Test
    void mapsIdempotencyResourceForeignKeyToSpecificConflict() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/invoices");
        var exception = new DataIntegrityViolationException(
                "insert violates foreign key constraint \"fk_idempotency_document_tenant\"");

        var response = handler.handleIntegrity(exception, request);

        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("IDEMPOTENCY_RESOURCE_CONFLICT", response.getBody().code());
    }

    @Test
    void mapsVoidingSequenceConstraintToSpecificConflict() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/documents/id/void");
        var exception = new DataIntegrityViolationException(
                "duplicate key value violates unique constraint \"uq_voiding_sequence\"");

        var response = handler.handleIntegrity(exception, request);

        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("VOIDING_SEQUENCE_CONFLICT", response.getBody().code());
    }

    @Test
    void reportsMissingQueryParameterAsBadRequest() {
        MockHttpServletRequest request = new MockHttpServletRequest("PATCH", "/api/v1/issuers/id/activation");
        var exception = new MissingServletRequestParameterException("active", "boolean");

        var response = handler.handleMissingParameter(exception, request);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("MISSING_PARAMETER", response.getBody().code());
        assertEquals("Parámetro obligatorio", response.getBody().validationErrors().get("active"));
    }
}
