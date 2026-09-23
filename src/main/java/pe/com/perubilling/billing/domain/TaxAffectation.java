package pe.com.perubilling.billing.domain;

import java.math.BigDecimal;
import java.util.Arrays;

public enum TaxAffectation {
    TAXABLE("10", Category.TAXABLE, TaxKind.IGV, false),
    FREE_AWARD("11", Category.TAXABLE, TaxKind.IGV, true),
    FREE_WITHDRAWAL("12", Category.TAXABLE, TaxKind.IGV, true),
    FREE_PROMOTION("13", Category.TAXABLE, TaxKind.IGV, true),
    FREE_EMPLOYEE("14", Category.TAXABLE, TaxKind.IGV, true),
    FREE_BONUS("15", Category.TAXABLE, TaxKind.IGV, true),
    FREE_OTHER("16", Category.TAXABLE, TaxKind.IGV, true),
    IVAP("17", Category.TAXABLE, TaxKind.IVAP, false),
    EXONERATED("20", Category.EXONERATED, TaxKind.NONE, false),
    FREE_EXONERATED("21", Category.EXONERATED, TaxKind.NONE, true),
    UNAFFECTED("30", Category.UNAFFECTED, TaxKind.NONE, false),
    FREE_UNAFFECTED_31("31", Category.UNAFFECTED, TaxKind.NONE, true),
    FREE_UNAFFECTED_32("32", Category.UNAFFECTED, TaxKind.NONE, true),
    FREE_UNAFFECTED_33("33", Category.UNAFFECTED, TaxKind.NONE, true),
    FREE_UNAFFECTED_34("34", Category.UNAFFECTED, TaxKind.NONE, true),
    FREE_UNAFFECTED_35("35", Category.UNAFFECTED, TaxKind.NONE, true),
    FREE_UNAFFECTED_36("36", Category.UNAFFECTED, TaxKind.NONE, true),
    FREE_UNAFFECTED_37("37", Category.UNAFFECTED, TaxKind.NONE, true),
    EXPORT("40", Category.EXPORT, TaxKind.NONE, false);

    public enum Category { TAXABLE, EXONERATED, UNAFFECTED, EXPORT }
    public enum TaxKind { IGV, IVAP, NONE }

    private final String code;
    private final Category category;
    private final TaxKind taxKind;
    private final boolean free;

    TaxAffectation(String code, Category category, TaxKind taxKind, boolean free) {
        this.code = code;
        this.category = category;
        this.taxKind = taxKind;
        this.free = free;
    }

    public String code() { return code; }
    public Category category() { return category; }
    public TaxKind taxKind() { return taxKind; }
    public boolean free() { return free; }

    public BigDecimal defaultRate() {
        return switch (taxKind) {
            case IGV -> new BigDecimal("18.00");
            case IVAP -> new BigDecimal("4.00");
            case NONE -> BigDecimal.ZERO.setScale(2);
        };
    }

    public String taxSchemeId() {
        return switch (taxKind) {
            case IGV -> "1000";
            case IVAP -> "1016";
            case NONE -> switch (category) {
                case EXONERATED -> "9997";
                case UNAFFECTED -> "9998";
                case EXPORT -> "9995";
                case TAXABLE -> throw new IllegalStateException("Afectación gravada sin impuesto");
            };
        };
    }

    public String taxSchemeName() {
        return switch (taxKind) {
            case IGV -> "IGV";
            case IVAP -> "IVAP";
            case NONE -> switch (category) {
                case EXONERATED -> "EXO";
                case UNAFFECTED -> "INA";
                case EXPORT -> "EXP";
                case TAXABLE -> throw new IllegalStateException("Afectación gravada sin impuesto");
            };
        };
    }

    public String taxTypeCode() {
        return switch (taxKind) {
            case IGV, IVAP -> "VAT";
            case NONE -> category == Category.EXONERATED ? "VAT" : "FRE";
        };
    }

    public String taxCategoryId() {
        if (taxKind == TaxKind.IGV || taxKind == TaxKind.IVAP) return "S";
        return switch (category) {
            case EXONERATED -> "E";
            case UNAFFECTED -> "O";
            case EXPORT -> "G";
            case TAXABLE -> "S";
        };
    }

    public static TaxAffectation fromCode(String code) {
        return Arrays.stream(values()).filter(v -> v.code.equals(code)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Código de afectación IGV no soportado: " + code));
    }
}
