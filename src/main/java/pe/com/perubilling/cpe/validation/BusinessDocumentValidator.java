package pe.com.perubilling.cpe.validation;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;
import pe.com.perubilling.billing.domain.AllowanceChargeEntity;
import pe.com.perubilling.billing.domain.DocumentAllowanceChargeEntity;
import pe.com.perubilling.billing.domain.PaymentMethod;
import pe.com.perubilling.billing.domain.TaxAffectation;
import pe.com.perubilling.cpe.domain.DocumentBundle;
import pe.com.perubilling.shared.domain.BusinessException;
import pe.com.perubilling.shared.domain.DocumentType;
import pe.com.perubilling.shared.sunat.catalog.SunatAllowanceChargeReason;

@Component
public class BusinessDocumentValidator {
    private static final Pattern NUMBER = Pattern.compile("[FB][A-Z0-9]{3}-[1-9][0-9]*");

    public void validate(DocumentBundle bundle) {
        var d = bundle.document();
        if (!NUMBER.matcher(d.getFullNumber()).matches()) {
            throw BusinessException.badRequest("INVALID_DOCUMENT_NUMBER", "Número de comprobante inválido");
        }
        if (bundle.items().isEmpty()) {
            throw BusinessException.badRequest("ITEMS_REQUIRED", "El comprobante debe contener al menos un ítem");
        }
        if (d.getTotalAmount().compareTo(BigDecimal.ZERO) < 0) {
            throw BusinessException.badRequest("INVALID_TOTAL", "El total no puede ser negativo");
        }
        if (d.getDocumentType() == DocumentType.INVOICE && !"6".equals(d.getCustomerDocumentType())) {
            throw BusinessException.badRequest("CUSTOMER_RUC_REQUIRED", "La factura requiere RUC del adquirente");
        }
        if ((d.getDocumentType() == DocumentType.CREDIT_NOTE || d.getDocumentType() == DocumentType.DEBIT_NOTE)
                && (d.getReferenceDocumentNumber() == null || d.getReasonCode() == null)) {
            throw BusinessException.badRequest("REFERENCE_REQUIRED", "La nota requiere documento de referencia y código de motivo");
        }

        BigDecimal calculatedTotal = BigDecimal.ZERO;
        BigDecimal calculatedIgv = BigDecimal.ZERO;
        BigDecimal calculatedIvap = BigDecimal.ZERO;
        BigDecimal calculatedFreeTax = BigDecimal.ZERO;
        BigDecimal calculatedAllowances = BigDecimal.ZERO;
        BigDecimal calculatedCharges = BigDecimal.ZERO;

        boolean hasOrphanAdjustment = bundle.allowanceCharges().stream()
                .anyMatch(adjustment -> adjustment.getDocumentItemId() == null
                        || bundle.items().stream().noneMatch(item -> Objects.equals(item.getId(), adjustment.getDocumentItemId())));
        if (hasOrphanAdjustment) {
            throw BusinessException.badRequest("ORPHAN_LINE_ADJUSTMENT",
                    "Existe un cargo/descuento que no pertenece a una línea del comprobante");
        }

        for (var item : bundle.items()) {
            TaxAffectation affectation = parseAffectation(item.getTaxAffectationCode());
            validateTaxSemantics(item.getLineNumber(), item.isFreeOperation(), item.getIgvRate(), affectation);

            BigDecimal expectedGross = money(item.getQuantity().multiply(item.getUnitValue()));
            assertMoney("LINE_GROSS_MISMATCH",
                    "El valor bruto de la línea " + item.getLineNumber() + " no coincide", expectedGross, item.getLineGrossAmount());

            List<AllowanceChargeEntity> lineAdjustments = bundle.allowanceCharges().stream()
                    .filter(adjustment -> Objects.equals(adjustment.getDocumentItemId(), item.getId()))
                    .toList();

            BigDecimal lineAllowance = BigDecimal.ZERO;
            BigDecimal lineCharge = BigDecimal.ZERO;
            int expectedSequence = 1;
            for (AllowanceChargeEntity adjustment : lineAdjustments) {
                if (adjustment.getSequenceNumber() != expectedSequence++) {
                    throw BusinessException.badRequest("INVALID_ADJUSTMENT_SEQUENCE",
                            "La secuencia de cargos/descuentos de la línea " + item.getLineNumber() + " es inválida");
                }
                if (adjustment.getLineNumber() != item.getLineNumber()) {
                    throw BusinessException.badRequest("ADJUSTMENT_LINE_MISMATCH",
                            "El cargo/descuento no corresponde a la línea almacenada");
                }
                SunatAllowanceChargeReason reason = parseAdjustmentReason(adjustment.getReasonCode());
                if (!reason.coreSupported() || reason.scope() != SunatAllowanceChargeReason.Scope.ITEM) {
                    throw BusinessException.badRequest("ALLOWANCE_CHARGE_REASON_NOT_ENABLED",
                            "El código SUNAT " + reason.code() + " no está habilitado para ajustes de línea CORE");
                }
                if (adjustment.isCharge() != reason.charge()) {
                    throw BusinessException.badRequest("ALLOWANCE_CHARGE_INDICATOR_MISMATCH",
                            "El indicador cargo/descuento no coincide con el Catálogo SUNAT 53");
                }
                if (adjustment.getFactor() == null || adjustment.getFactor().signum() <= 0
                        || adjustment.getFactor().compareTo(BigDecimal.ONE) > 0) {
                    throw BusinessException.badRequest("INVALID_ALLOWANCE_CHARGE_FACTOR",
                            "El factor de cargo/descuento almacenado es inválido");
                }
                assertMoney("ADJUSTMENT_BASE_MISMATCH",
                        "La base del cargo/descuento no coincide con el valor bruto de la línea",
                        expectedGross, adjustment.getBaseAmount());
                BigDecimal expectedAdjustmentAmount = money(expectedGross.multiply(adjustment.getFactor()));
                assertMoney("ADJUSTMENT_AMOUNT_MISMATCH",
                        "El importe del cargo/descuento no corresponde a su factor y base",
                        expectedAdjustmentAmount, adjustment.getAmount());
                if (adjustment.isCharge()) lineCharge = lineCharge.add(adjustment.getAmount());
                else lineAllowance = lineAllowance.add(adjustment.getAmount());
            }

            if (affectation.free() && !lineAdjustments.isEmpty()) {
                throw BusinessException.badRequest("FREE_LINE_ADJUSTMENTS_UNSUPPORTED",
                        "Las operaciones gratuitas no admiten cargos o descuentos en el alcance CORE");
            }
            if (!affectation.free() && expectedGross.signum() > 0 && lineAllowance.compareTo(expectedGross) >= 0) {
                throw BusinessException.badRequest("INVALID_LINE_DISCOUNT",
                        "Los descuentos de una línea onerosa deben ser menores que su valor bruto");
            }

            lineAllowance = money(lineAllowance);
            lineCharge = money(lineCharge);
            assertMoney("LINE_ALLOWANCE_MISMATCH",
                    "Los descuentos de la línea no coinciden con el importe almacenado", lineAllowance, item.getLineAllowanceAmount());
            assertMoney("LINE_CHARGE_MISMATCH",
                    "Los cargos de la línea no coinciden con el importe almacenado", lineCharge, item.getLineChargeAmount());

            BigDecimal expectedBase = affectation.free() ? expectedGross : money(expectedGross.subtract(lineAllowance));
            assertMoney("LINE_BASE_MISMATCH",
                    "La base imponible de la línea no coincide después de aplicar descuentos", expectedBase, item.getLineBaseAmount());

            if (affectation.free()) {
                if (item.getLineTotalAmount().signum() != 0 || item.getUnitPrice().signum() != 0) {
                    throw BusinessException.badRequest("INVALID_FREE_TOTAL",
                            "Las operaciones gratuitas no pueden incrementar el importe pagable");
                }
                calculatedFreeTax = calculatedFreeTax.add(item.getLineIgvAmount());
            } else {
                BigDecimal expectedLineTotal = money(item.getLineBaseAmount()
                        .add(item.getLineIgvAmount())
                        .add(item.getLineIcbperAmount())
                        .add(lineCharge));
                assertMoney("LINE_TOTAL_MISMATCH",
                        "El total pagable de la línea no coincide con su base, tributos y cargos",
                        expectedLineTotal, item.getLineTotalAmount());
                calculatedTotal = calculatedTotal.add(item.getLineTotalAmount());
                if (affectation.taxKind() == TaxAffectation.TaxKind.IGV) {
                    calculatedIgv = calculatedIgv.add(item.getLineIgvAmount());
                } else if (affectation.taxKind() == TaxAffectation.TaxKind.IVAP) {
                    calculatedIvap = calculatedIvap.add(item.getLineIgvAmount());
                }
            }
            calculatedAllowances = calculatedAllowances.add(lineAllowance);
            calculatedCharges = calculatedCharges.add(lineCharge);
        }

        BigDecimal documentBase = money(calculatedTotal);
        BigDecimal globalAllowances = BigDecimal.ZERO;
        BigDecimal globalCharges = BigDecimal.ZERO;
        int expectedGlobalSequence = 1;
        for (DocumentAllowanceChargeEntity adjustment : bundle.documentAllowanceCharges()) {
            if (adjustment.getSequenceNumber() != expectedGlobalSequence++) {
                throw BusinessException.badRequest("INVALID_GLOBAL_ADJUSTMENT_SEQUENCE",
                        "La secuencia de cargos/descuentos globales es inválida");
            }
            SunatAllowanceChargeReason reason = parseAdjustmentReason(adjustment.getReasonCode());
            if (!reason.coreSupported() || reason.scope() != SunatAllowanceChargeReason.Scope.GLOBAL) {
                throw BusinessException.badRequest("GLOBAL_ALLOWANCE_CHARGE_REASON_NOT_ENABLED",
                        "El código SUNAT " + reason.code() + " no está habilitado para ajustes globales CORE");
            }
            if (reason.affectsTaxBase()) {
                throw BusinessException.badRequest("GLOBAL_BASE_AFFECTING_ADJUSTMENT_UNSUPPORTED",
                        "Los ajustes globales que afectan la base imponible no están habilitados en CORE");
            }
            if (adjustment.isCharge() != reason.charge()) {
                throw BusinessException.badRequest("ALLOWANCE_CHARGE_INDICATOR_MISMATCH",
                        "El indicador del ajuste global no coincide con el Catálogo SUNAT 53");
            }
            if (adjustment.getFactor() == null || adjustment.getFactor().signum() <= 0
                    || adjustment.getFactor().compareTo(BigDecimal.ONE) > 0) {
                throw BusinessException.badRequest("INVALID_ALLOWANCE_CHARGE_FACTOR",
                        "El factor del ajuste global almacenado es inválido");
            }
            assertMoney("GLOBAL_ADJUSTMENT_BASE_MISMATCH",
                    "La base del ajuste global no coincide con el importe previo del documento",
                    documentBase, adjustment.getBaseAmount());
            BigDecimal expectedAmount = money(documentBase.multiply(adjustment.getFactor()));
            assertMoney("GLOBAL_ADJUSTMENT_AMOUNT_MISMATCH",
                    "El importe del ajuste global no corresponde a su factor y base",
                    expectedAmount, adjustment.getAmount());
            if (adjustment.isCharge()) globalCharges = globalCharges.add(adjustment.getAmount());
            else globalAllowances = globalAllowances.add(adjustment.getAmount());
        }

        calculatedTotal = money(calculatedTotal.subtract(globalAllowances).add(globalCharges));
        calculatedAllowances = calculatedAllowances.add(globalAllowances);
        calculatedCharges = calculatedCharges.add(globalCharges);

        assertMoney("TOTAL_MISMATCH", "El total calculado no coincide con el total del documento", calculatedTotal, d.getTotalAmount());
        assertMoney("IGV_MISMATCH", "El IGV de líneas no coincide con el total de IGV", calculatedIgv, d.getIgvAmount());
        assertMoney("IVAP_MISMATCH", "El IVAP de líneas no coincide con el total de IVAP", calculatedIvap, d.getIvapAmount());
        assertMoney("FREE_TAX_MISMATCH", "El tributo referencial gratuito no coincide", calculatedFreeTax, d.getFreeTaxAmount());
        assertMoney("ALLOWANCE_TOTAL_MISMATCH", "El total de descuentos no coincide", calculatedAllowances, d.getAllowanceTotalAmount());
        assertMoney("CHARGE_TOTAL_MISMATCH", "El total de cargos no coincide", calculatedCharges, d.getChargeTotalAmount());

        validatePayment(bundle);
    }

    private TaxAffectation parseAffectation(String code) {
        try {
            return TaxAffectation.fromCode(code);
        } catch (IllegalArgumentException ex) {
            throw BusinessException.badRequest("UNSUPPORTED_TAX_AFFECTATION", ex.getMessage());
        }
    }

    private SunatAllowanceChargeReason parseAdjustmentReason(String code) {
        try {
            return SunatAllowanceChargeReason.fromCode(code);
        } catch (IllegalArgumentException ex) {
            throw BusinessException.badRequest("UNSUPPORTED_ALLOWANCE_CHARGE_REASON", ex.getMessage());
        }
    }

    private void validateTaxSemantics(int lineNumber, boolean freeOperation, BigDecimal rate, TaxAffectation affectation) {
        if (freeOperation != affectation.free()) {
            throw BusinessException.badRequest("FREE_OPERATION_MISMATCH",
                    "La línea " + lineNumber + " no coincide con su código de afectación");
        }
        if (affectation.taxKind() == TaxAffectation.TaxKind.IVAP
                && rate.compareTo(new BigDecimal("4.00")) != 0) {
            throw BusinessException.badRequest("INVALID_IVAP_RATE",
                    "La línea IVAP " + lineNumber + " debe usar 4%");
        }
        if (affectation.taxKind() == TaxAffectation.TaxKind.NONE && rate.signum() != 0) {
            throw BusinessException.badRequest("INVALID_ZERO_RATE",
                    "La línea " + lineNumber + " debe usar tasa 0");
        }
    }

    private void validatePayment(DocumentBundle bundle) {
        var d = bundle.document();
        if (d.getDocumentType() != DocumentType.INVOICE) return;
        if (d.getPaymentMethod() == null) {
            throw BusinessException.badRequest("PAYMENT_TERMS_REQUIRED",
                    "La factura debe indicar forma de pago Contado o Credito");
        }
        if (d.getPaymentMethod() == PaymentMethod.CONTADO) {
            if (d.getPendingAmount() != null && d.getPendingAmount().signum() != 0) {
                throw BusinessException.badRequest("INVALID_CASH_PAYMENT", "Factura al contado con saldo pendiente");
            }
            if (!bundle.installments().isEmpty()) {
                throw BusinessException.badRequest("INVALID_CASH_INSTALLMENTS", "Factura al contado no puede contener cuotas");
            }
            return;
        }
        if (d.getPendingAmount() == null || d.getPendingAmount().signum() <= 0) {
            throw BusinessException.badRequest("PENDING_AMOUNT_REQUIRED", "Factura a crédito sin monto neto pendiente");
        }
        if (bundle.installments().isEmpty()) {
            throw BusinessException.badRequest("INSTALLMENTS_REQUIRED", "Factura a crédito sin cuotas");
        }
        BigDecimal sum = bundle.installments().stream()
                .map(i -> i.getAmount())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        assertMoney("INSTALLMENT_TOTAL_MISMATCH",
                "La suma de cuotas no coincide con el monto neto pendiente", sum, d.getPendingAmount());
    }

    private void assertMoney(String code, String message, BigDecimal actual, BigDecimal expected) {
        if (actual == null || expected == null
                || actual.setScale(2, RoundingMode.HALF_UP).compareTo(expected.setScale(2, RoundingMode.HALF_UP)) != 0) {
            throw BusinessException.badRequest(code, message);
        }
    }

    private BigDecimal money(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP);
    }
}
