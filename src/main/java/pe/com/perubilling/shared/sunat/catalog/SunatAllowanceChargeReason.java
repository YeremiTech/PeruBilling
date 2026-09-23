package pe.com.perubilling.shared.sunat.catalog;

import java.util.Arrays;

public enum SunatAllowanceChargeReason {
    ITEM_DISCOUNT_AFFECTS_BASE("00", false, Scope.ITEM, true,
            "DESCUENTOS QUE AFECTAN LA BASE IMPONIBLE DEL IGV/IVAP", true),
    ITEM_DISCOUNT_NO_BASE_EFFECT("01", false, Scope.ITEM, false,
            "DESCUENTOS QUE NO AFECTAN LA BASE IMPONIBLE DEL IGV/IVAP", false),
    GLOBAL_DISCOUNT_AFFECTS_BASE("02", false, Scope.GLOBAL, true,
            "DESCUENTOS GLOBALES QUE AFECTAN LA BASE IMPONIBLE DEL IGV/IVAP", false),
    GLOBAL_DISCOUNT_NO_BASE_EFFECT("03", false, Scope.GLOBAL, false,
            "DESCUENTOS GLOBALES QUE NO AFECTAN LA BASE IMPONIBLE DEL IGV/IVAP", true),
    GLOBAL_ADVANCE_TAXABLE("04", false, Scope.GLOBAL, true,
            "DESCUENTOS GLOBALES POR ANTICIPOS GRAVADOS", false),
    GLOBAL_ADVANCE_EXONERATED("05", false, Scope.GLOBAL, true,
            "DESCUENTOS GLOBALES POR ANTICIPOS EXONERADOS", false),
    GLOBAL_ADVANCE_UNAFFECTED("06", false, Scope.GLOBAL, true,
            "DESCUENTOS GLOBALES POR ANTICIPOS INAFECTOS", false),
    ITEM_CHARGE_AFFECTS_BASE("47", true, Scope.ITEM, true,
            "CARGOS QUE AFECTAN LA BASE IMPONIBLE DEL IGV/IVAP", false),
    ITEM_CHARGE_NO_BASE_EFFECT("48", true, Scope.ITEM, false,
            "CARGOS QUE NO AFECTAN LA BASE IMPONIBLE DEL IGV/IVAP", true),
    GLOBAL_CHARGE_AFFECTS_BASE("49", true, Scope.GLOBAL, true,
            "CARGOS GLOBALES QUE AFECTAN LA BASE IMPONIBLE DEL IGV/IVAP", false),
    GLOBAL_CHARGE_NO_BASE_EFFECT("50", true, Scope.GLOBAL, false,
            "CARGOS GLOBALES QUE NO AFECTAN LA BASE IMPONIBLE DEL IGV/IVAP", true),
    INTERNAL_SALE_PERCEPTION("51", true, Scope.GLOBAL, false,
            "PERCEPCIÓN VENTA INTERNA", false),
    FUEL_PERCEPTION("52", true, Scope.GLOBAL, false,
            "PERCEPCIÓN A LA ADQUISICIÓN DE COMBUSTIBLE", false),
    SPECIAL_PERCEPTION("53", true, Scope.GLOBAL, false,
            "PERCEPCIÓN REALIZADA AL AGENTE DE PERCEPCIÓN CON TASA ESPECIAL", false);

    public enum Scope { ITEM, GLOBAL }

    private final String code;
    private final boolean charge;
    private final Scope scope;
    private final boolean affectsTaxBase;
    private final String description;
    private final boolean coreSupported;

    SunatAllowanceChargeReason(String code, boolean charge, Scope scope, boolean affectsTaxBase,
                               String description, boolean coreSupported) {
        this.code = code;
        this.charge = charge;
        this.scope = scope;
        this.affectsTaxBase = affectsTaxBase;
        this.description = description;
        this.coreSupported = coreSupported;
    }

    public String code() { return code; }
    public boolean charge() { return charge; }
    public Scope scope() { return scope; }
    public boolean affectsTaxBase() { return affectsTaxBase; }
    public String description() { return description; }
    public boolean coreSupported() { return coreSupported; }

    public static SunatAllowanceChargeReason fromCode(String code) {
        return Arrays.stream(values()).filter(value -> value.code.equals(code)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "Código SUNAT de cargo/descuento no reconocido: " + code));
    }
}
