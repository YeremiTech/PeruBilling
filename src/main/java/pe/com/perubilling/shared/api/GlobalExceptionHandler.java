package pe.com.perubilling.shared.api;

import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.transaction.TransactionSystemException;
import org.springframework.web.HttpMediaTypeNotAcceptableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import pe.com.perubilling.shared.domain.BusinessException;

@RestControllerAdvice
@ApiResponses({
        @ApiResponse(responseCode = "400", ref = "#/components/responses/BadRequest"),
        @ApiResponse(responseCode = "401", ref = "#/components/responses/Unauthorized"),
        @ApiResponse(responseCode = "403", ref = "#/components/responses/Forbidden"),
        @ApiResponse(responseCode = "404", ref = "#/components/responses/NotFound"),
        @ApiResponse(responseCode = "409", ref = "#/components/responses/Conflict"),
        @ApiResponse(responseCode = "413", ref = "#/components/responses/PayloadTooLarge"),
        @ApiResponse(responseCode = "415", ref = "#/components/responses/UnsupportedMediaType"),
        @ApiResponse(responseCode = "422", ref = "#/components/responses/UnprocessableEntity"),
        @ApiResponse(responseCode = "429", ref = "#/components/responses/TooManyRequests"),
        @ApiResponse(responseCode = "500", ref = "#/components/responses/InternalServerError"),
        @ApiResponse(responseCode = "503", ref = "#/components/responses/ServiceUnavailable")
})
public class GlobalExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    private static final Pattern CONSTRAINT_PATTERN = Pattern.compile(
            "(?:constraint|restricci[oó]n)\\s+[\"']?([a-zA-Z0-9_]+)", Pattern.CASE_INSENSITIVE);

    private final ApiErrorFactory errorFactory;

    public GlobalExceptionHandler(ApiErrorFactory errorFactory) {
        this.errorFactory = errorFactory;
    }

    @ExceptionHandler(BusinessException.class)
    ResponseEntity<ApiError> handleBusiness(BusinessException ex, HttpServletRequest request) {
        return build(ex.getStatus(), ex.getCode(), ex.getMessage(), request, Map.of());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ApiError> handleValidation(MethodArgumentNotValidException ex, HttpServletRequest request) {
        Map<String, String> errors = new LinkedHashMap<>();
        ex.getBindingResult().getFieldErrors()
                .forEach(error -> errors.putIfAbsent(error.getField(), error.getDefaultMessage()));
        ex.getBindingResult().getGlobalErrors()
                .forEach(error -> errors.putIfAbsent("_request", error.getDefaultMessage()));
        return build(
                HttpStatus.BAD_REQUEST,
                "VALIDATION_ERROR",
                "La solicitud contiene datos inválidos",
                request,
                errors);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    ResponseEntity<ApiError> handleConstraint(ConstraintViolationException ex, HttpServletRequest request) {
        Map<String, String> errors = new LinkedHashMap<>();
        ex.getConstraintViolations().forEach(violation ->
                errors.putIfAbsent(violation.getPropertyPath().toString(), violation.getMessage()));
        return build(
                HttpStatus.BAD_REQUEST,
                "CONSTRAINT_VIOLATION",
                "La solicitud no cumple las restricciones requeridas",
                request,
                errors);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<ApiError> handleUnreadable(HttpMessageNotReadableException ex, HttpServletRequest request) {
        return build(HttpStatus.BAD_REQUEST, "INVALID_JSON", "El cuerpo JSON no es válido", request, Map.of());
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    ResponseEntity<ApiError> handleMissingParameter(
            MissingServletRequestParameterException ex,
            HttpServletRequest request) {
        return build(
                HttpStatus.BAD_REQUEST,
                "MISSING_PARAMETER",
                "Falta el parámetro obligatorio: " + ex.getParameterName(),
                request,
                Map.of(ex.getParameterName(), "Parámetro obligatorio"));
    }

    @ExceptionHandler(MissingRequestHeaderException.class)
    ResponseEntity<ApiError> handleMissingHeader(MissingRequestHeaderException ex, HttpServletRequest request) {
        return build(
                HttpStatus.BAD_REQUEST,
                "MISSING_HEADER",
                "Falta la cabecera obligatoria: " + ex.getHeaderName(),
                request,
                Map.of(ex.getHeaderName(), "Cabecera obligatoria"));
    }

    @ExceptionHandler(MissingServletRequestPartException.class)
    ResponseEntity<ApiError> handleMissingPart(MissingServletRequestPartException ex, HttpServletRequest request) {
        return build(
                HttpStatus.BAD_REQUEST,
                "MISSING_MULTIPART_PART",
                "Falta la parte multipart obligatoria: " + ex.getRequestPartName(),
                request,
                Map.of(ex.getRequestPartName(), "Parte multipart obligatoria"));
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    ResponseEntity<ApiError> handleTypeMismatch(MethodArgumentTypeMismatchException ex, HttpServletRequest request) {
        return build(
                HttpStatus.BAD_REQUEST,
                "INVALID_PARAMETER",
                "El parámetro '" + ex.getName() + "' tiene un formato inválido",
                request,
                Map.of(ex.getName(), "Formato inválido"));
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    ResponseEntity<ApiError> handleMaxUploadSize(MaxUploadSizeExceededException ex, HttpServletRequest request) {
        return build(
                HttpStatus.PAYLOAD_TOO_LARGE,
                "PAYLOAD_TOO_LARGE",
                "El archivo o la solicitud supera el tamaño máximo permitido",
                request,
                Map.of());
    }

    @ExceptionHandler(MultipartException.class)
    ResponseEntity<ApiError> handleMultipart(MultipartException ex, HttpServletRequest request) {
        return build(
                HttpStatus.BAD_REQUEST,
                "INVALID_MULTIPART_REQUEST",
                "La solicitud multipart/form-data no es válida",
                request,
                Map.of());
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    ResponseEntity<ApiError> handleUnsupportedMediaType(
            HttpMediaTypeNotSupportedException ex,
            HttpServletRequest request) {
        return build(
                HttpStatus.UNSUPPORTED_MEDIA_TYPE,
                "UNSUPPORTED_MEDIA_TYPE",
                "El Content-Type de la solicitud no está soportado para este endpoint",
                request,
                Map.of());
    }

    @ExceptionHandler(HttpMediaTypeNotAcceptableException.class)
    ResponseEntity<ApiError> handleNotAcceptable(
            HttpMediaTypeNotAcceptableException ex,
            HttpServletRequest request) {
        return build(
                HttpStatus.NOT_ACCEPTABLE,
                "NOT_ACCEPTABLE",
                "No existe una representación compatible con la cabecera Accept solicitada",
                request,
                Map.of());
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    ResponseEntity<ApiError> handleMethodNotAllowed(
            HttpRequestMethodNotSupportedException ex,
            HttpServletRequest request) {
        return build(
                HttpStatus.METHOD_NOT_ALLOWED,
                "METHOD_NOT_ALLOWED",
                "El método HTTP no está permitido para este recurso",
                request,
                Map.of());
    }

    @ExceptionHandler(AccessDeniedException.class)
    ResponseEntity<ApiError> handleAccessDenied(AccessDeniedException ex, HttpServletRequest request) {
        return build(
                HttpStatus.FORBIDDEN,
                "FORBIDDEN",
                "No tiene permisos para realizar esta operación",
                request,
                Map.of());
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    ResponseEntity<ApiError> handleIntegrity(DataIntegrityViolationException ex, HttpServletRequest request) {
        ConflictDescriptor conflict = classifyIntegrityConflict(ex);
        log.warn(
                "Conflicto de integridad en {} {} requestId={} code={} constraint={} rootCause={}",
                request.getMethod(),
                request.getRequestURI(),
                errorFactory.requestId(request),
                conflict.code(),
                constraintName(ex),
                rootCauseClass(ex));
        return build(HttpStatus.CONFLICT, conflict.code(), conflict.message(), request, Map.of());
    }

    @ExceptionHandler(PessimisticLockingFailureException.class)
    ResponseEntity<ApiError> handleLocking(Exception ex, HttpServletRequest request) {
        log.warn(
                "Contención de bloqueo en {} {} requestId={}",
                request.getMethod(),
                request.getRequestURI(),
                errorFactory.requestId(request));
        return build(
                HttpStatus.CONFLICT,
                "RESOURCE_BUSY",
                "El recurso está siendo modificado por otra operación. Intente nuevamente",
                request,
                Map.of());
    }

    @ExceptionHandler(DataAccessResourceFailureException.class)
    ResponseEntity<ApiError> handleDatabaseUnavailable(
            DataAccessResourceFailureException ex,
            HttpServletRequest request) {
        log.error(
                "Base de datos no disponible en {} {} requestId={}",
                request.getMethod(),
                request.getRequestURI(),
                errorFactory.requestId(request),
                ex);
        return build(
                HttpStatus.SERVICE_UNAVAILABLE,
                "DATABASE_UNAVAILABLE",
                "La base de datos no está disponible temporalmente",
                request,
                Map.of());
    }

    @ExceptionHandler(TransactionSystemException.class)
    ResponseEntity<ApiError> handleTransaction(TransactionSystemException ex, HttpServletRequest request) {
        DataIntegrityViolationException integrity = findCause(ex, DataIntegrityViolationException.class);
        if (integrity != null) {
            return handleIntegrity(integrity, request);
        }
        log.error(
                "Fallo transaccional en {} {} requestId={}",
                request.getMethod(),
                request.getRequestURI(),
                errorFactory.requestId(request),
                ex);
        return build(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "TRANSACTION_ERROR",
                "No se pudo completar la operación transaccional",
                request,
                Map.of());
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ApiError> handleUnexpected(Exception ex, HttpServletRequest request) {
        log.error(
                "Error inesperado en {} {} requestId={}",
                request.getMethod(),
                request.getRequestURI(),
                errorFactory.requestId(request),
                ex);
        return build(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "INTERNAL_ERROR",
                "Ocurrió un error interno",
                request,
                Map.of());
    }

    private ResponseEntity<ApiError> build(
            HttpStatus status,
            String code,
            String message,
            HttpServletRequest request,
            Map<String, String> errors) {
        return ResponseEntity.status(status).body(errorFactory.create(status, code, message, request, errors));
    }

    private ConflictDescriptor classifyIntegrityConflict(DataIntegrityViolationException ex) {
        String text = completeCauseText(ex).toLowerCase(Locale.ROOT);
        if (containsAny(text, "uq_document_series")) {
            return new ConflictDescriptor("SERIES_ALREADY_EXISTS", "La serie ya está registrada para este emisor");
        }
        if (containsAny(text, "uq_certificate_issuer_fingerprint", "digital_certificate_fingerprint_key")) {
            return new ConflictDescriptor(
                    "CERTIFICATE_ALREADY_EXISTS",
                    "El certificado ya está registrado para este emisor");
        }
        if (containsAny(text, "uq_issuer_tenant_ruc")) {
            return new ConflictDescriptor("RUC_EXISTS", "El RUC ya está registrado en este tenant");
        }
        if (containsAny(text, "user_account_email_key")) {
            return new ConflictDescriptor("EMAIL_ALREADY_EXISTS", "El correo ya está registrado");
        }
        if (containsAny(text, "uq_idempotency_key")) {
            return new ConflictDescriptor(
                    "IDEMPOTENCY_CONFLICT",
                    "La clave de idempotencia entra en conflicto con una operación existente");
        }
        if (containsAny(text, "fk_idempotency_document_tenant", "idempotency_record_resource_id_fkey")) {
            return new ConflictDescriptor(
                    "IDEMPOTENCY_RESOURCE_CONFLICT",
                    "No se pudo asociar la clave de idempotencia al comprobante creado");
        }
        if (containsAny(text, "uq_document_external_id")) {
            return new ConflictDescriptor(
                    "EXTERNAL_ID_ALREADY_EXISTS",
                    "externalId ya está asociado a otro comprobante del mismo emisor");
        }
        if (containsAny(text, "uq_document_number", "uq_document_full_number")) {
            return new ConflictDescriptor(
                    "DOCUMENT_NUMBER_CONFLICT",
                    "El número de comprobante ya está registrado");
        }
        if (containsAny(text, "uq_voiding_document")) {
            return new ConflictDescriptor(
                    "VOID_ALREADY_REQUESTED",
                    "El documento ya tiene una solicitud de baja registrada");
        }
        if (containsAny(text, "uq_voiding_identifier", "uq_voiding_sequence")) {
            return new ConflictDescriptor(
                    "VOIDING_SEQUENCE_CONFLICT",
                    "No se pudo reservar una secuencia única para la Comunicación de Baja");
        }
        if (containsAny(text, "fk_voiding_document_tenant", "fk_voiding_issuer_tenant")) {
            return new ConflictDescriptor(
                    "VOIDING_REFERENCE_CONFLICT",
                    "La Comunicación de Baja no pudo asociarse al emisor o comprobante");
        }
        if (containsAny(text, "uq_daily_summary_sequence", "uq_daily_summary_identifier")) {
            return new ConflictDescriptor(
                    "SUMMARY_ALREADY_EXISTS",
                    "Ya existe un resumen diario con el mismo identificador");
        }
        return new ConflictDescriptor(
                "DATA_CONFLICT",
                "La operación entra en conflicto con datos existentes");
    }

    private String constraintName(Throwable throwable) {
        String text = completeCauseText(throwable);
        Matcher matcher = CONSTRAINT_PATTERN.matcher(text);
        return matcher.find() ? matcher.group(1) : "unknown";
    }

    private String rootCauseClass(Throwable throwable) {
        Throwable current = throwable;
        Throwable last = throwable;
        int depth = 0;
        while (current != null && depth++ < 16) {
            last = current;
            current = current.getCause();
        }
        return last == null ? "unknown" : last.getClass().getSimpleName();
    }
    private boolean containsAny(String text, String... values) {
        for (String value : values) {
            if (text.contains(value)) {
                return true;
            }
        }
        return false;
    }

    private String completeCauseText(Throwable throwable) {
        StringBuilder value = new StringBuilder();
        Throwable current = throwable;
        int depth = 0;
        while (current != null && depth++ < 12) {
            if (current.getMessage() != null) {
                value.append(' ').append(current.getMessage());
            }
            current = current.getCause();
        }
        return value.toString();
    }

    private <T extends Throwable> T findCause(Throwable throwable, Class<T> type) {
        Throwable current = throwable;
        int depth = 0;
        while (current != null && depth++ < 12) {
            if (type.isInstance(current)) {
                return type.cast(current);
            }
            current = current.getCause();
        }
        return null;
    }

    private record ConflictDescriptor(String code, String message) {}
}
