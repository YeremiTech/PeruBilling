package pe.com.perubilling.billing.application;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Component;
import pe.com.perubilling.billing.api.CreateDocumentRequest;
import pe.com.perubilling.billing.api.PaymentRequest;
import pe.com.perubilling.billing.domain.DocumentStatus;
import pe.com.perubilling.billing.domain.PaymentMethod;
import pe.com.perubilling.billing.domain.TaxAffectation;
import pe.com.perubilling.billing.infrastructure.ElectronicDocumentRepository;
import pe.com.perubilling.billing.tax.TaxCalculation;
import pe.com.perubilling.issuer.application.RucValidator;
import pe.com.perubilling.shared.domain.BusinessException;
import pe.com.perubilling.shared.domain.DocumentType;
import pe.com.perubilling.shared.sunat.SunatNoteReasonCatalog;
import pe.com.perubilling.shared.sunat.catalog.SunatIdentityDocumentType;
import pe.com.perubilling.shared.sunat.catalog.SunatOperationType;
import pe.com.perubilling.shared.sunat.catalog.SunatUnitCode;

@Component
public class DocumentRequestValidator {
    private static final ZoneId LIMA = ZoneId.of("America/Lima");
    private static final BigDecimal RECEIPT_IDENTIFICATION_THRESHOLD = new BigDecimal("700.00");
    private static final Set<String> ISO_COUNTRY_CODES = Set.of(Locale.getISOCountries());
    private static final Set<String> EXPORT_IDENTITY_TYPES = Set.of("0", "4", "7", "A", "B", "C", "D");

    private final ElectronicDocumentRepository documents;
    private final RucValidator rucValidator;

    public DocumentRequestValidator(ElectronicDocumentRepository documents, RucValidator rucValidator) {
        this.documents = documents;
        this.rucValidator = rucValidator;
    }

    public void validate(
            DocumentType type,
            CreateDocumentRequest request,
            LocalDate issueDate,
            UUID tenantId,
            TaxCalculation calculation) {
        String operationCode = request.operationType() == null || request.operationType().isBlank()
                ? "0101" : request.operationType();
        validateOperationType(type, request, operationCode, calculation);
        validateCustomerFormat(request);
        validateUnits(request);

        if (requiresDailySummary(type, request) && calculation.chargeTotal().signum() > 0) {
            throw BusinessException.badRequest("SUMMARY_LINE_CHARGES_UNSUPPORTED",
                    "Los cargos todavía no están habilitados para comprobantes procesados mediante Resumen Diario");
        }

        String series = request.series().toUpperCase(Locale.ROOT);
        validateSeries(type, request, series);
        validateCustomerRules(type, request, operationCode, calculation);

        if (issueDate.isAfter(LocalDate.now(LIMA))) {
            throw BusinessException.badRequest("FUTURE_ISSUE_DATE",
                    "La fecha de emisión no puede estar en el futuro");
        }

        if (type == DocumentType.CREDIT_NOTE || type == DocumentType.DEBIT_NOTE) {
            validateReference(type, request, tenantId);
        } else if (request.payment() != null && type != DocumentType.INVOICE) {
            throw BusinessException.badRequest("PAYMENT_TERMS_NOT_ALLOWED",
                    "La forma de pago SUNAT se modela en esta versión para facturas; las notas heredan el contexto del comprobante modificado");
        }
    }

    public PaymentRequest normalizePayment(
            DocumentType type, PaymentRequest requested, BigDecimal total, LocalDate issueDate) {
        if (type != DocumentType.INVOICE) return null;

        PaymentRequest payment = requested == null
                ? new PaymentRequest(PaymentMethod.CONTADO, BigDecimal.ZERO, List.of())
                : requested;

        if (payment.method() == PaymentMethod.CONTADO) {
            if (payment.pendingAmount() != null && payment.pendingAmount().signum() != 0) {
                throw BusinessException.badRequest("INVALID_CASH_PAYMENT",
                        "Una factura al contado no debe tener monto pendiente");
            }
            if (payment.installments() != null && !payment.installments().isEmpty()) {
                throw BusinessException.badRequest("INVALID_CASH_INSTALLMENTS",
                        "Una factura al contado no debe contener cuotas");
            }
            return new PaymentRequest(PaymentMethod.CONTADO, BigDecimal.ZERO.setScale(2), List.of());
        }

        BigDecimal pending = payment.pendingAmount();
        if (pending == null || pending.signum() <= 0 || pending.compareTo(total) > 0) {
            throw BusinessException.badRequest("INVALID_PENDING_AMOUNT",
                    "La factura a crédito requiere un monto neto pendiente mayor a cero y no superior al total");
        }
        if (payment.installments() == null || payment.installments().isEmpty()) {
            throw BusinessException.badRequest("INSTALLMENTS_REQUIRED",
                    "La factura a crédito requiere al menos una cuota");
        }

        BigDecimal sum = BigDecimal.ZERO;
        for (var installment : payment.installments()) {
            if (installment.dueDate().isBefore(issueDate)) {
                throw BusinessException.badRequest("INVALID_INSTALLMENT_DATE",
                        "La fecha de vencimiento de una cuota no puede ser anterior a la fecha de emisión");
            }
            sum = sum.add(installment.amount());
        }
        if (money(sum).compareTo(money(pending)) != 0) {
            throw BusinessException.badRequest("INSTALLMENT_TOTAL_MISMATCH",
                    "La suma de las cuotas debe coincidir con el monto neto pendiente");
        }
        return new PaymentRequest(PaymentMethod.CREDITO, money(pending), List.copyOf(payment.installments()));
    }

    public boolean requiresDailySummary(DocumentType type, CreateDocumentRequest request) {
        if (type == DocumentType.RECEIPT) return true;
        return (type == DocumentType.CREDIT_NOTE || type == DocumentType.DEBIT_NOTE)
                && request.reference() != null
                && "03".equals(request.reference().documentType());
    }

    private void validateOperationType(
            DocumentType type,
            CreateDocumentRequest request,
            String operationCode,
            TaxCalculation calculation) {
        try {
            SunatOperationType operation = SunatOperationType.fromCode(operationCode);
            if (!operation.coreSupported()) {
                throw BusinessException.badRequest("UNSUPPORTED_CORE_OPERATION_TYPE",
                        "El tipo de operación " + operationCode + " todavía no está habilitado en el alcance CORE");
            }
        } catch (IllegalArgumentException ex) {
            throw BusinessException.badRequest("INVALID_OPERATION_TYPE", ex.getMessage());
        }

        boolean hasIvap = calculation.items().stream()
                .anyMatch(item -> item.taxKind() == TaxAffectation.TaxKind.IVAP);
        boolean hasExport = calculation.items().stream()
                .anyMatch(item -> TaxAffectation.fromCode(item.source().taxAffectationCode()) == TaxAffectation.EXPORT);
        boolean allExport = calculation.items().stream()
                .allMatch(item -> TaxAffectation.fromCode(item.source().taxAffectationCode()) == TaxAffectation.EXPORT);

        if ("0102".equals(operationCode)) {
            if (type != DocumentType.INVOICE) {
                throw BusinessException.badRequest("EXPORT_INVOICE_REQUIRED",
                        "El perfil de exportación 0102 está habilitado únicamente para factura electrónica");
            }
            if (!allExport || !hasExport) {
                throw BusinessException.badRequest("EXPORT_LINES_REQUIRED",
                        "La operación 0102 requiere que todas las líneas usen afectación 40 (Exportación)");
            }
            if (calculation.igv().signum() != 0 || calculation.ivap().signum() != 0
                    || calculation.icbper().signum() != 0 || calculation.free().signum() != 0
                    || calculation.taxable().signum() != 0 || calculation.ivapTaxable().signum() != 0
                    || calculation.exonerated().signum() != 0 || calculation.unaffected().signum() != 0) {
                throw BusinessException.badRequest("INVALID_EXPORT_TAX_TOTALS",
                        "La exportación CORE no puede mezclar IGV, IVAP, ICBPER, gratuidad ni operaciones internas");
            }
            validateExportCustomer(request);
        } else if (hasExport) {
            throw BusinessException.badRequest("EXPORT_OPERATION_TYPE_REQUIRED",
                    "Las líneas con afectación 40 requieren tipo de operación 0102");
        }

        if (hasIvap && !"0107".equals(operationCode)) {
            throw BusinessException.badRequest("IVAP_OPERATION_TYPE_REQUIRED",
                    "Las líneas IVAP requieren tipo de operación 0107");
        }
        if ("0107".equals(operationCode) && !hasIvap) {
            throw BusinessException.badRequest("IVAP_LINE_REQUIRED",
                    "El tipo de operación 0107 requiere al menos una línea IVAP");
        }
    }

    private void validateCustomerFormat(CreateDocumentRequest request) {
        if (request.customer() == null) return;
        try {
            SunatIdentityDocumentType type = SunatIdentityDocumentType.fromCode(request.customer().documentType());
            if (!type.validNumberFormat(request.customer().documentNumber())) {
                throw BusinessException.badRequest("INVALID_CUSTOMER_DOCUMENT_NUMBER",
                        "El número del documento del adquirente no cumple el formato CORE del Catálogo 06");
            }
        } catch (IllegalArgumentException ex) {
            throw BusinessException.badRequest("UNSUPPORTED_CUSTOMER_DOCUMENT_TYPE", ex.getMessage());
        }
        String countryCode = normalizeCountry(request.customer().countryCode());
        if (countryCode != null && !ISO_COUNTRY_CODES.contains(countryCode)) {
            throw BusinessException.badRequest("INVALID_CUSTOMER_COUNTRY",
                    "customer.countryCode debe ser un código ISO 3166-1 alpha-2 válido");
        }
    }

    private void validateUnits(CreateDocumentRequest request) {
        for (var item : request.items()) {
            try {
                SunatUnitCode.fromCode(item.unitCode());
            } catch (IllegalArgumentException ex) {
                throw BusinessException.badRequest("INVALID_UNIT_CODE", ex.getMessage());
            }
            boolean hasGtin = item.gtin() != null && !item.gtin().isBlank();
            boolean hasScheme = item.gtinSchemeId() != null && !item.gtinSchemeId().isBlank();
            if (hasGtin != hasScheme) {
                throw BusinessException.badRequest("INVALID_GTIN",
                        "gtin y gtinSchemeId deben informarse juntos");
            }
        }
    }

    private void validateSeries(DocumentType type, CreateDocumentRequest request, String series) {
        if (type == DocumentType.INVOICE && !series.startsWith("F")) {
            throw BusinessException.badRequest("INVALID_SERIES", "La factura debe usar una serie iniciada en F");
        }
        if (type == DocumentType.RECEIPT && !series.startsWith("B")) {
            throw BusinessException.badRequest("INVALID_SERIES", "La boleta debe usar una serie iniciada en B");
        }
        if ((type == DocumentType.CREDIT_NOTE || type == DocumentType.DEBIT_NOTE) && request.reference() == null) {
            throw BusinessException.badRequest("REFERENCE_REQUIRED",
                    "Las notas deben indicar el comprobante de referencia y el motivo");
        }
        if ((type == DocumentType.INVOICE || type == DocumentType.RECEIPT) && request.reference() != null) {
            throw BusinessException.badRequest("REFERENCE_NOT_ALLOWED",
                    "Factura y boleta no deben contener referencia de nota");
        }
    }

    private void validateCustomerRules(
            DocumentType type, CreateDocumentRequest request, String operationCode, TaxCalculation calculation) {
        if (type == DocumentType.INVOICE && request.customer() == null) {
            throw BusinessException.badRequest("CUSTOMER_REQUIRED", "La factura debe identificar al adquirente");
        }
        if ((type == DocumentType.CREDIT_NOTE || type == DocumentType.DEBIT_NOTE) && request.customer() == null) {
            throw BusinessException.badRequest("CUSTOMER_REQUIRED",
                    "La nota debe identificar al mismo adquirente del comprobante modificado");
        }
        if (type == DocumentType.RECEIPT && request.customer() == null) {
            if (!"PEN".equalsIgnoreCase(request.currency())) {
                throw BusinessException.badRequest("ANONYMOUS_RECEIPT_CURRENCY_UNSUPPORTED",
                        "La boleta sin identificación solo se admite en PEN para aplicar de forma segura el umbral normativo");
            }
            if (money(calculation.total()).compareTo(RECEIPT_IDENTIFICATION_THRESHOLD) > 0) {
                throw BusinessException.badRequest("CUSTOMER_REQUIRED_OVER_700",
                        "La boleta con importe total mayor a S/ 700 debe identificar al adquirente");
            }
        }
        boolean exportInvoice = type == DocumentType.INVOICE && "0102".equals(operationCode);
        if (type == DocumentType.INVOICE && request.customer() != null && !exportInvoice
                && !"6".equals(request.customer().documentType())) {
            throw BusinessException.badRequest("RUC_REQUIRED",
                    "La factura debe identificar al adquirente con tipo de documento 6 (RUC), salvo exportación 0102");
        }
        if (request.customer() != null && "6".equals(request.customer().documentType())
                && !rucValidator.isValid(request.customer().documentNumber())) {
            throw BusinessException.badRequest("INVALID_CUSTOMER_RUC",
                    "El RUC del adquirente no supera la validación de dígito verificador");
        }
    }

    private void validateExportCustomer(CreateDocumentRequest request) {
        if (request.customer() == null) {
            throw BusinessException.badRequest("EXPORT_CUSTOMER_REQUIRED",
                    "La factura de exportación debe identificar al adquirente no domiciliado");
        }
        if (!EXPORT_IDENTITY_TYPES.contains(request.customer().documentType())) {
            throw BusinessException.badRequest("EXPORT_CUSTOMER_DOCUMENT_TYPE_INVALID",
                    "La exportación 0102 requiere un tipo de documento no domiciliado del Catálogo 06");
        }
        String countryCode = normalizeCountry(request.customer().countryCode());
        if (countryCode == null || !ISO_COUNTRY_CODES.contains(countryCode) || "PE".equals(countryCode)) {
            throw BusinessException.badRequest("EXPORT_CUSTOMER_COUNTRY_REQUIRED",
                    "La exportación 0102 requiere un país ISO 3166-1 válido y distinto de PE");
        }
    }

    private String normalizeCountry(String value) {
        return value == null || value.isBlank() ? null : value.trim().toUpperCase(Locale.ROOT);
    }

    private void validateReference(DocumentType noteType, CreateDocumentRequest request, UUID tenantId) {
        String referenceType = request.reference().documentType();
        DocumentType referencedType = switch (referenceType) {
            case "01" -> DocumentType.INVOICE;
            case "03" -> DocumentType.RECEIPT;
            default -> throw BusinessException.badRequest("INVALID_REFERENCE_TYPE",
                    "Las notas implementadas solo pueden modificar factura (01) o boleta (03)");
        };

        if (!SunatNoteReasonCatalog.isSupported(noteType, request.reference().reasonCode(), referenceType)) {
            throw BusinessException.badRequest("INVALID_NOTE_REASON",
                    "El motivo de la nota no pertenece al catálogo CORE soportado. "
                            + SunatNoteReasonCatalog.supportedDescription(noteType, referenceType));
        }

        String expectedPrefix = referencedType == DocumentType.RECEIPT ? "B" : "F";
        if (!request.series().toUpperCase(Locale.ROOT).startsWith(expectedPrefix)) {
            throw BusinessException.badRequest("INVALID_NOTE_SERIES",
                    "La serie de la nota debe iniciar en " + expectedPrefix + " según el comprobante modificado");
        }

        var referenced = documents.findByTenantIdAndIssuerIdAndDocumentTypeAndFullNumber(
                        tenantId, request.issuerId(), referencedType,
                        request.reference().number().toUpperCase(Locale.ROOT))
                .orElseThrow(() -> BusinessException.badRequest("REFERENCE_NOT_FOUND",
                        "El comprobante de referencia no existe para este emisor"));

        if (referenced.getStatus() != DocumentStatus.ACCEPTED && referenced.getStatus() != DocumentStatus.OBSERVED) {
            throw BusinessException.badRequest("REFERENCE_NOT_ACCEPTED",
                    "La nota solo puede modificar un comprobante aceptado por SUNAT");
        }
        if (!referenced.getCustomerDocumentType().equals(request.customer().documentType())
                || !referenced.getCustomerDocumentNumber().equals(request.customer().documentNumber().trim())) {
            throw BusinessException.badRequest("REFERENCE_CUSTOMER_MISMATCH",
                    "El adquirente de la nota no coincide con el comprobante modificado");
        }
    }

    private BigDecimal money(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP);
    }
}
