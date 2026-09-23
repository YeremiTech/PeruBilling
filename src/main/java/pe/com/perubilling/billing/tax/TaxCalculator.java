package pe.com.perubilling.billing.tax;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import pe.com.perubilling.billing.api.AllowanceChargeRequest;
import pe.com.perubilling.billing.api.DocumentItemRequest;
import pe.com.perubilling.billing.domain.TaxAffectation;
import pe.com.perubilling.shared.domain.BusinessException;
import pe.com.perubilling.shared.sunat.catalog.SunatAllowanceChargeReason;
import pe.com.perubilling.shared.sunat.catalog.SunatAllowanceChargeReason.Scope;

@Component
public class TaxCalculator {
    private static final BigDecimal HUNDRED = new BigDecimal("100");
    private static final ZoneId LIMA = ZoneId.of("America/Lima");
    private final IcbperRatePolicy icbperRatePolicy;

    @Autowired
    public TaxCalculator(IcbperRatePolicy icbperRatePolicy) {
        this.icbperRatePolicy = icbperRatePolicy;
    }

    TaxCalculator() {
        this(new IcbperRatePolicy());
    }

    public TaxCalculation calculate(List<DocumentItemRequest> requests) {
        return calculate(requests, List.of(), LocalDate.now(LIMA));
    }

    public TaxCalculation calculate(
            List<DocumentItemRequest> requests,
            List<AllowanceChargeRequest> documentAdjustments,
            LocalDate issueDate) {
        BigDecimal taxable = zero(), ivapTaxable = zero(), exonerated = zero(), unaffected = zero(), export = zero();
        BigDecimal free = zero(), igv = zero(), ivap = zero(), freeTax = zero(), icbper = zero();
        BigDecimal allowanceTotal = zero(), chargeTotal = zero(), total = zero();
        List<TaxCalculation.CalculatedItem> items = new ArrayList<>();
        int line = 1;

        for (DocumentItemRequest request : requests) {
            TaxAffectation affectation = parseAffectation(request.taxAffectationCode());
            BigDecimal quantity = requirePositive(request.quantity(), "quantity");
            BigDecimal unitValue = requireNonNegative(request.unitValue(), "unitValue");
            BigDecimal grossBase = money(quantity.multiply(unitValue));
            BigDecimal rate = request.igvRate() == null ? affectation.defaultRate() : request.igvRate();

            validateRate(affectation, rate);
            List<TaxCalculation.CalculatedAdjustment> adjustments = calculateAdjustments(
                    request.safeAdjustments(), grossBase, affectation.free(), Scope.ITEM);
            BigDecimal lineAllowance = adjustments.stream()
                    .filter(adjustment -> !adjustment.charge())
                    .map(TaxCalculation.CalculatedAdjustment::amount)
                    .reduce(zero(), BigDecimal::add);
            BigDecimal lineCharge = adjustments.stream()
                    .filter(TaxCalculation.CalculatedAdjustment::charge)
                    .map(TaxCalculation.CalculatedAdjustment::amount)
                    .reduce(zero(), BigDecimal::add);

            if (!affectation.free() && lineAllowance.compareTo(grossBase) >= 0 && grossBase.signum() > 0) {
                throw BusinessException.badRequest("INVALID_LINE_DISCOUNT",
                        "Los descuentos de una línea onerosa deben ser menores que su valor bruto");
            }

            BigDecimal base = affectation.free() ? grossBase : money(grossBase.subtract(lineAllowance));
            BigDecimal calculatedTax = affectation.taxKind() == TaxAffectation.TaxKind.NONE
                    ? zero()
                    : money(base.multiply(rate).divide(HUNDRED, 8, RoundingMode.HALF_UP));
            BigDecimal lineTax = affectation.free() ? zero() : calculatedTax;

            BigDecimal icbperPerUnit = icbperRatePolicy.validateAndResolve(issueDate, request.icbperPerUnit());
            BigDecimal lineIcbper = money(quantity.multiply(icbperPerUnit));

            BigDecimal unitPrice = affectation.free()
                    ? zero6()
                    : base.add(lineTax)
                            .divide(quantity, 8, RoundingMode.HALF_UP)
                            .setScale(6, RoundingMode.HALF_UP);
            BigDecimal lineTotal = affectation.free()
                    ? zero()
                    : money(base.add(lineTax).add(lineIcbper).add(lineCharge));

            if (affectation.free()) {
                free = free.add(base);
                freeTax = freeTax.add(calculatedTax);
            } else {
                switch (affectation.category()) {
                    case TAXABLE -> {
                        if (affectation.taxKind() == TaxAffectation.TaxKind.IVAP) ivapTaxable = ivapTaxable.add(base);
                        else taxable = taxable.add(base);
                    }
                    case EXONERATED -> exonerated = exonerated.add(base);
                    case UNAFFECTED -> unaffected = unaffected.add(base);
                    case EXPORT -> export = export.add(base);
                }
                if (affectation.taxKind() == TaxAffectation.TaxKind.IGV) igv = igv.add(lineTax);
                if (affectation.taxKind() == TaxAffectation.TaxKind.IVAP) ivap = ivap.add(lineTax);
            }

            icbper = icbper.add(lineIcbper);
            allowanceTotal = allowanceTotal.add(lineAllowance);
            chargeTotal = chargeTotal.add(lineCharge);
            total = total.add(lineTotal);
            items.add(new TaxCalculation.CalculatedItem(
                    request, line++, unitPrice, grossBase, money(lineAllowance), money(lineCharge), base,
                    lineTax, icbperPerUnit, lineIcbper, lineTotal, affectation.free(),
                    rate.setScale(2, RoundingMode.HALF_UP), affectation.taxKind(), List.copyOf(adjustments)));
        }

        BigDecimal documentBase = money(total);
        List<TaxCalculation.CalculatedAdjustment> globalAdjustments = calculateAdjustments(
                documentAdjustments == null ? List.of() : documentAdjustments,
                documentBase,
                false,
                Scope.GLOBAL);
        BigDecimal globalAllowance = globalAdjustments.stream()
                .filter(adjustment -> !adjustment.charge())
                .map(TaxCalculation.CalculatedAdjustment::amount)
                .reduce(zero(), BigDecimal::add);
        BigDecimal globalCharge = globalAdjustments.stream()
                .filter(TaxCalculation.CalculatedAdjustment::charge)
                .map(TaxCalculation.CalculatedAdjustment::amount)
                .reduce(zero(), BigDecimal::add);

        if (globalAllowance.compareTo(documentBase) >= 0 && documentBase.signum() > 0) {
            throw BusinessException.badRequest("INVALID_GLOBAL_DISCOUNT",
                    "Los descuentos globales CORE deben ser menores que el importe previo del documento");
        }
        allowanceTotal = allowanceTotal.add(globalAllowance);
        chargeTotal = chargeTotal.add(globalCharge);
        total = money(total.subtract(globalAllowance).add(globalCharge));

        return new TaxCalculation(
                money(taxable), money(ivapTaxable), money(exonerated), money(unaffected), money(export),
                money(free), money(igv), money(ivap), money(freeTax), money(icbper),
                money(allowanceTotal), money(chargeTotal), money(total), List.copyOf(items),
                List.copyOf(globalAdjustments));
    }

    private List<TaxCalculation.CalculatedAdjustment> calculateAdjustments(
            List<AllowanceChargeRequest> requests,
            BigDecimal baseAmount,
            boolean freeOperation,
            Scope expectedScope) {
        if (requests.isEmpty()) return List.of();
        if (freeOperation) {
            throw BusinessException.badRequest("FREE_LINE_ADJUSTMENTS_UNSUPPORTED",
                    "Las operaciones gratuitas no admiten descuentos o cargos en el alcance CORE");
        }
        if (baseAmount.signum() <= 0) {
            throw BusinessException.badRequest("ADJUSTMENT_BASE_REQUIRED",
                    "Un cargo/descuento requiere una base mayor que cero");
        }

        List<TaxCalculation.CalculatedAdjustment> result = new ArrayList<>();
        int sequence = 1;
        for (AllowanceChargeRequest request : requests) {
            SunatAllowanceChargeReason reason;
            try {
                reason = SunatAllowanceChargeReason.fromCode(request.reasonCode());
            } catch (IllegalArgumentException ex) {
                throw BusinessException.badRequest("UNSUPPORTED_ALLOWANCE_CHARGE_REASON", ex.getMessage());
            }
            if (reason.scope() != expectedScope) {
                throw BusinessException.badRequest("ALLOWANCE_CHARGE_SCOPE_MISMATCH",
                        "El código SUNAT " + reason.code() + " corresponde a nivel " + reason.scope());
            }
            if (!reason.coreSupported()) {
                throw BusinessException.badRequest("ALLOWANCE_CHARGE_REASON_NOT_ENABLED",
                        "El código SUNAT " + reason.code() + " requiere un módulo tributario aún no habilitado");
            }
            if (request.charge() == null || request.charge() != reason.charge()) {
                throw BusinessException.badRequest("ALLOWANCE_CHARGE_INDICATOR_MISMATCH",
                        "El indicador cargo/descuento no coincide con el Catálogo SUNAT 53 para el código " + reason.code());
            }
            if (reason.affectsTaxBase() && expectedScope == Scope.GLOBAL) {
                throw BusinessException.badRequest("GLOBAL_BASE_AFFECTING_ADJUSTMENT_UNSUPPORTED",
                        "Los ajustes globales que afectan base imponible aún no están habilitados en CORE");
            }
            BigDecimal factor = request.factor();
            if (factor == null || factor.signum() <= 0 || factor.compareTo(BigDecimal.ONE) > 0) {
                throw BusinessException.badRequest("INVALID_ALLOWANCE_CHARGE_FACTOR",
                        "El factor de cargo/descuento debe estar entre 0 y 1");
            }
            BigDecimal amount = money(baseAmount.multiply(factor));
            if (amount.signum() <= 0) {
                throw BusinessException.badRequest("ALLOWANCE_CHARGE_ROUNDS_TO_ZERO",
                        "El cargo/descuento es demasiado pequeño y redondea a 0.00");
            }
            result.add(new TaxCalculation.CalculatedAdjustment(
                    sequence++, reason.charge(), reason.code(), factor.setScale(6, RoundingMode.HALF_UP),
                    amount, baseAmount));
        }
        return result;
    }

    private TaxAffectation parseAffectation(String code) {
        try { return TaxAffectation.fromCode(code); }
        catch (IllegalArgumentException ex) {
            throw BusinessException.badRequest("UNSUPPORTED_TAX_AFFECTATION", ex.getMessage());
        }
    }

    private void validateRate(TaxAffectation affectation, BigDecimal rate) {
        if (rate == null || rate.signum() < 0)
            throw BusinessException.badRequest("INVALID_TAX_RATE", "La tasa tributaria no puede ser negativa");
        if (affectation.taxKind() == TaxAffectation.TaxKind.NONE && rate.signum() != 0)
            throw BusinessException.badRequest("INVALID_TAX_RATE", "La afectación " + affectation.code() + " debe usar tasa 0");
        if (affectation.taxKind() == TaxAffectation.TaxKind.IVAP && rate.compareTo(new BigDecimal("4.00")) != 0)
            throw BusinessException.badRequest("INVALID_IVAP_RATE", "La afectación 17 (IVAP) usa tasa 4% en esta versión");
        if (affectation.taxKind() == TaxAffectation.TaxKind.IGV && rate.compareTo(new BigDecimal("18.00")) != 0)
            throw BusinessException.badRequest("UNSUPPORTED_IGV_RATE", "Esta versión soporta IGV 18%; no se emitirá un XML con una tasa no calibrada");
    }

    private BigDecimal requirePositive(BigDecimal value, String field) {
        if (value == null || value.signum() <= 0)
            throw BusinessException.badRequest("INVALID_" + field.toUpperCase(), field + " debe ser mayor que cero");
        return value;
    }

    private BigDecimal requireNonNegative(BigDecimal value, String field) {
        if (value == null || value.signum() < 0)
            throw BusinessException.badRequest("INVALID_" + field.toUpperCase(), field + " no puede ser negativo");
        return value;
    }

    private BigDecimal money(BigDecimal value) { return value.setScale(2, RoundingMode.HALF_UP); }
    private BigDecimal zero() { return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP); }
    private BigDecimal zero6() { return BigDecimal.ZERO.setScale(6, RoundingMode.HALF_UP); }
}
