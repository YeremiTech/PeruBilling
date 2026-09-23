package pe.com.perubilling.shared.sunat.catalog;

import java.util.Arrays;

public enum SunatPriceType {
    UNIT_PRICE("01", "Precio unitario", true),
    REFERENCE_VALUE("02", "Valor referencial unitario en operaciones no onerosas", true);

    private final String code;
    private final String description;
    private final boolean coreSupported;

    SunatPriceType(String code, String description, boolean coreSupported) {
        this.code = code;
        this.description = description;
        this.coreSupported = coreSupported;
    }

    public String code() { return code; }
    public String description() { return description; }
    public boolean coreSupported() { return coreSupported; }

    public static SunatPriceType fromCode(String code) {
        return Arrays.stream(values()).filter(value -> value.code.equals(code)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Tipo de precio SUNAT no reconocido: " + code));
    }
}
