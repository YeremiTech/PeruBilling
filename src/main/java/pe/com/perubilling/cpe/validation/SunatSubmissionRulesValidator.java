package pe.com.perubilling.cpe.validation;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.Currency;
import java.util.Locale;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import pe.com.perubilling.billing.domain.PaymentMethod;
import pe.com.perubilling.billing.domain.TaxAffectation;
import pe.com.perubilling.cpe.domain.DocumentBundle;
import pe.com.perubilling.shared.domain.BusinessException;
import pe.com.perubilling.shared.domain.DocumentType;
import pe.com.perubilling.shared.sunat.catalog.SunatAllowanceChargeReason;
import pe.com.perubilling.shared.sunat.catalog.SunatIdentityDocumentType;
import pe.com.perubilling.shared.sunat.catalog.SunatOperationType;
import pe.com.perubilling.shared.sunat.catalog.SunatUnitCode;

/**
 * Gate semántico de reglas núcleo antes del transporte SUNAT.
 *
 * Este componente es deliberadamente versionado. Cubre reglas de seguridad y del
 * alcance funcional soportado por PeruBilling, pero NO se presenta como reemplazo
 * del catálogo oficial completo de reglas SUNAT. El gate de producción se completa
 * con XSD oficiales y fixtures/regresión contra SUNAT BETA.
 */
@Component
public class SunatSubmissionRulesValidator {
    private static final ZoneId LIMA = ZoneId.of("America/Lima");
    private static final BigDecimal IDENTIFICATION_THRESHOLD = new BigDecimal("700.00");
    private static final Set<String> ISO_COUNTRY_CODES = Set.of(Locale.getISOCountries());
    private static final Set<String> EXPORT_IDENTITY_TYPES = Set.of("0", "4", "7", "A", "B", "C", "D");
    private final int maxDirectAgeDays;
    private final int maxSummaryAgeDays;
    private final String rulesVersion;

    public SunatSubmissionRulesValidator(
            @Value("${app.sunat.rules.max-direct-age-days:3}") int maxDirectAgeDays,
            @Value("${app.sunat.rules.max-summary-age-days:7}") int maxSummaryAgeDays,
            @Value("${app.sunat.rules.version:2026-08-26}") String rulesVersion) {
        this.maxDirectAgeDays = Math.max(0, maxDirectAgeDays);
        this.maxSummaryAgeDays = Math.max(0, maxSummaryAgeDays);
        this.rulesVersion = rulesVersion;
    }

    public void validateForDirectSubmission(DocumentBundle bundle) {
        validateCore(bundle);
        var document = bundle.document();
        if (requiresDailySummary(document.getDocumentType(), document.getReferenceDocumentType())) return;
        validateAge(document.getIssueDate(), maxDirectAgeDays,
                "DIRECT_SUBMISSION_DEADLINE_EXCEEDED",
                "El documento excede el plazo configurado para envío directo a SUNAT");
    }

    public void validateForSummary(DocumentBundle bundle) {
        validateCore(bundle);
        if (!requiresDailySummary(bundle.document().getDocumentType(), bundle.document().getReferenceDocumentType())) {
            throw BusinessException.badRequest("SUMMARY_DOCUMENT_TYPE_INVALID",
                    "El documento no corresponde al flujo de Resumen Diario");
        }
        validateSummaryReferenceDate(bundle.document().getIssueDate());
    }

    public void validateSummaryReferenceDate(LocalDate referenceDate) {
        validateAge(referenceDate, maxSummaryAgeDays,
                "SUMMARY_DEADLINE_EXCEEDED",
                "La fecha de referencia excede el plazo configurado para Resumen Diario SUNAT");
    }

    public String rulesVersion() { return rulesVersion; }
    public String coverage() { return "CORE"; }

    private void validateCore(DocumentBundle bundle) {
        var d = bundle.document();
        validateAgeNotFuture(d.getIssueDate());

        SunatOperationType operationType;
        try {
            operationType = SunatOperationType.fromCode(d.getOperationType());
        } catch (IllegalArgumentException ex) {
            fail("INVALID_OPERATION_TYPE", ex.getMessage());
            return;
        }
        if (!operationType.coreSupported()) {
            fail("UNSUPPORTED_CORE_OPERATION_TYPE",
                    "El tipo de operación " + d.getOperationType()
                            + " no pertenece al alcance CORE certificado de esta versión");
        }
        validateCurrency(d.getCurrency());
        validateIdentityDocumentType(d.getCustomerDocumentType(), d.getCustomerDocumentNumber());

        if (d.getDocumentType() == DocumentType.INVOICE) {
            if ("0102".equals(d.getOperationType())) {
                validateExportCustomer(d);
            } else if (!"6".equals(d.getCustomerDocumentType()) || d.getCustomerDocumentNumber() == null
                    || d.getCustomerDocumentNumber().length() != 11) {
                fail("INVOICE_CUSTOMER_RUC_REQUIRED",
                        "La factura requiere RUC de 11 dígitos del adquirente, salvo exportación 0102");
            }
        }

        if (d.getDocumentType() == DocumentType.RECEIPT && isAnonymous(d)) {
            if (!"PEN".equals(d.getCurrency())) {
                fail("ANONYMOUS_RECEIPT_CURRENCY_UNSUPPORTED",
                        "La boleta sin identificación está habilitada únicamente en PEN");
            }
            if (money(d.getTotalAmount()).compareTo(IDENTIFICATION_THRESHOLD) > 0) {
                fail("CUSTOMER_REQUIRED_OVER_700",
                        "La boleta con importe total mayor a S/ 700 debe identificar al adquirente");
            }
        }

        if ((d.getDocumentType() == DocumentType.CREDIT_NOTE || d.getDocumentType() == DocumentType.DEBIT_NOTE)
                && (!"01".equals(d.getReferenceDocumentType()) && !"03".equals(d.getReferenceDocumentType()))) {
            fail("INVALID_REFERENCE_TYPE", "La nota soportada debe referenciar factura 01 o boleta 03");
        }

        if (d.getDocumentType() == DocumentType.INVOICE && d.getPaymentMethod() == null) {
            fail("PAYMENT_TERMS_REQUIRED", "La factura debe indicar forma de pago Contado o Credito");
        }
        if (d.getPaymentMethod() == PaymentMethod.CREDITO && bundle.installments().isEmpty()) {
            fail("INSTALLMENTS_REQUIRED", "La factura a crédito debe incluir cuotas");
        }

        for (var adjustment : bundle.allowanceCharges()) {
            SunatAllowanceChargeReason reason;
            try {
                reason = SunatAllowanceChargeReason.fromCode(adjustment.getReasonCode());
            } catch (IllegalArgumentException ex) {
                fail("UNSUPPORTED_ALLOWANCE_CHARGE_REASON", ex.getMessage());
                return;
            }
            if (!reason.coreSupported() || reason.scope() != SunatAllowanceChargeReason.Scope.ITEM) {
                fail("ALLOWANCE_CHARGE_REASON_NOT_ENABLED",
                        "El código SUNAT " + reason.code() + " no está habilitado para ajustes de línea CORE");
            }
            if (reason.charge() != adjustment.isCharge()) {
                fail("ALLOWANCE_CHARGE_INDICATOR_MISMATCH",
                        "El indicador cargo/descuento no coincide con el Catálogo SUNAT 53");
            }
        }

        for (var adjustment : bundle.documentAllowanceCharges()) {
            SunatAllowanceChargeReason reason;
            try {
                reason = SunatAllowanceChargeReason.fromCode(adjustment.getReasonCode());
            } catch (IllegalArgumentException ex) {
                fail("UNSUPPORTED_ALLOWANCE_CHARGE_REASON", ex.getMessage());
                return;
            }
            if (!reason.coreSupported() || reason.scope() != SunatAllowanceChargeReason.Scope.GLOBAL) {
                fail("GLOBAL_ALLOWANCE_CHARGE_REASON_NOT_ENABLED",
                        "El código SUNAT " + reason.code() + " no está habilitado para ajustes globales CORE");
            }
            if (reason.affectsTaxBase()) {
                fail("GLOBAL_BASE_AFFECTING_ADJUSTMENT_UNSUPPORTED",
                        "Los ajustes globales que afectan la base imponible no están habilitados en CORE");
            }
            if (reason.charge() != adjustment.isCharge()) {
                fail("ALLOWANCE_CHARGE_INDICATOR_MISMATCH",
                        "El indicador del ajuste global no coincide con el Catálogo SUNAT 53");
            }
        }

        if (requiresDailySummary(d.getDocumentType(), d.getReferenceDocumentType())
                && d.getChargeTotalAmount() != null && d.getChargeTotalAmount().signum() > 0) {
            fail("SUMMARY_LINE_CHARGES_UNSUPPORTED",
                    "Los cargos por línea quedan bloqueados en documentos enviados por Resumen Diario hasta calibrar su representación regulatoria");
        }

        boolean hasIvap = false;
        boolean hasExport = false;
        boolean allExport = !bundle.items().isEmpty();
        for (var item : bundle.items()) {
            try {
                SunatUnitCode.fromCode(item.getUnitCode());
            } catch (IllegalArgumentException ex) {
                fail("INVALID_UNIT_CODE",
                        "La unidad de medida de la línea " + item.getLineNumber() + " no está habilitada en CORE");
            }
            try {
                TaxAffectation affectation = TaxAffectation.fromCode(item.getTaxAffectationCode());
                hasExport |= affectation == TaxAffectation.EXPORT;
                allExport &= affectation == TaxAffectation.EXPORT;
                hasIvap |= affectation == TaxAffectation.IVAP;
            } catch (IllegalArgumentException ex) {
                fail("UNSUPPORTED_TAX_AFFECTATION", ex.getMessage());
            }
        }
        if ("0102".equals(d.getOperationType())) {
            if (d.getDocumentType() != DocumentType.INVOICE) {
                fail("EXPORT_INVOICE_REQUIRED",
                        "El perfil de exportación 0102 está habilitado únicamente para factura electrónica");
            }
            if (!hasExport || !allExport || d.getExportAmount() == null || d.getExportAmount().signum() <= 0) {
                fail("EXPORT_LINES_REQUIRED",
                        "La operación 0102 requiere exclusivamente líneas con afectación 40 y valor de exportación positivo");
            }
            if (d.getIgvAmount().signum() != 0 || d.getIvapAmount().signum() != 0
                    || d.getIcbperAmount().signum() != 0 || d.getFreeAmount().signum() != 0
                    || d.getTaxableAmount().signum() != 0 || d.getIvapTaxableAmount().signum() != 0
                    || d.getExoneratedAmount().signum() != 0 || d.getUnaffectedAmount().signum() != 0) {
                fail("INVALID_EXPORT_TAX_TOTALS",
                        "La exportación CORE no puede mezclar impuestos u operaciones internas");
            }
        } else if (hasExport) {
            fail("EXPORT_OPERATION_TYPE_REQUIRED",
                    "Las líneas con afectación 40 requieren tipo de operación 0102");
        }
        if (hasIvap && !"0107".equals(d.getOperationType())) {
            fail("IVAP_OPERATION_TYPE_REQUIRED", "Las líneas IVAP requieren tipo de operación 0107");
        }
        if ("0107".equals(d.getOperationType()) && !hasIvap) {
            fail("IVAP_LINE_REQUIRED", "El tipo de operación 0107 requiere al menos una línea IVAP");
        }
    }


    private void validateExportCustomer(pe.com.perubilling.billing.domain.ElectronicDocumentEntity document) {
        if (!EXPORT_IDENTITY_TYPES.contains(document.getCustomerDocumentType())) {
            fail("EXPORT_CUSTOMER_DOCUMENT_TYPE_INVALID",
                    "La exportación 0102 requiere un tipo de documento no domiciliado del Catálogo 06");
        }
        String countryCode = document.getCustomerCountryCode();
        if (countryCode == null || !ISO_COUNTRY_CODES.contains(countryCode.toUpperCase(Locale.ROOT))
                || "PE".equalsIgnoreCase(countryCode)) {
            fail("EXPORT_CUSTOMER_COUNTRY_REQUIRED",
                    "La exportación 0102 requiere país ISO 3166-1 válido y distinto de PE");
        }
    }

    private void validateIdentityDocumentType(String code, String number) {
        try {
            SunatIdentityDocumentType type = SunatIdentityDocumentType.fromCode(code);
            if (!type.validNumberFormat(number)) {
                fail("INVALID_CUSTOMER_DOCUMENT_NUMBER",
                        "El número del documento del adquirente no cumple el formato CORE del Catálogo 06");
            }
        } catch (IllegalArgumentException ex) {
            fail("UNSUPPORTED_CUSTOMER_DOCUMENT_TYPE", ex.getMessage());
        }
    }

    private void validateCurrency(String currency) {
        try {
            if (currency == null || currency.length() != 3) throw new IllegalArgumentException();
            Currency.getInstance(currency.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            fail("INVALID_CURRENCY", "Código de moneda ISO 4217 inválido");
        }
    }

    private void validateAgeNotFuture(LocalDate date) {
        if (date == null) fail("ISSUE_DATE_REQUIRED", "La fecha de emisión es obligatoria");
        if (date.isAfter(LocalDate.now(LIMA))) fail("FUTURE_ISSUE_DATE", "La fecha de emisión no puede estar en el futuro");
    }

    private void validateAge(LocalDate date, int maxDays, String code, String message) {
        validateAgeNotFuture(date);
        long age = ChronoUnit.DAYS.between(date, LocalDate.now(LIMA));
        if (age > maxDays) {
            throw BusinessException.badRequest(code,
                    message + " (reglas " + rulesVersion + ", antigüedad=" + age + " días, máximo=" + maxDays + ")");
        }
    }

    private boolean isAnonymous(pe.com.perubilling.billing.domain.ElectronicDocumentEntity d) {
        return "0".equals(d.getCustomerDocumentType()) && "-".equals(d.getCustomerDocumentNumber());
    }

    private boolean requiresDailySummary(DocumentType type, String referenceType) {
        if (type == DocumentType.RECEIPT) return true;
        return (type == DocumentType.CREDIT_NOTE || type == DocumentType.DEBIT_NOTE) && "03".equals(referenceType);
    }

    private BigDecimal money(BigDecimal value) { return value.setScale(2, RoundingMode.HALF_UP); }

    private void fail(String code, String message) {
        throw BusinessException.badRequest(code, message + " (reglas " + rulesVersion + ")");
    }
}
