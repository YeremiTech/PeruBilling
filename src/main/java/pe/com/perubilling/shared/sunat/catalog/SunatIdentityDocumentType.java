package pe.com.perubilling.shared.sunat.catalog;

import java.util.Arrays;
import java.util.regex.Pattern;

/** Catálogo SUNAT 06 de tipos de documentos de identidad. */
public enum SunatIdentityDocumentType {
    NON_DOMICILED_WITHOUT_RUC("0", "DOC.TRIB.NO.DOM.SIN.RUC", Pattern.compile("[-A-Z0-9]{1,20}")),
    DNI("1", "DOC. NACIONAL DE IDENTIDAD", Pattern.compile("\\d{8}")),
    FOREIGNER_CARD("4", "CARNET DE EXTRANJERIA", Pattern.compile("[A-Z0-9]{8,15}")),
    RUC("6", "REG. UNICO DE CONTRIBUYENTES", Pattern.compile("\\d{11}")),
    PASSPORT("7", "PASAPORTE", Pattern.compile("[A-Z0-9]{6,15}")),
    DIPLOMATIC_ID("A", "CED. DIPLOMATICA DE IDENTIDAD", Pattern.compile("[A-Z0-9-]{1,20}")),
    RESIDENCE_COUNTRY_ID("B", "DOC.IDENT.PAIS.RESIDENCIA-NO.D", Pattern.compile("[A-Z0-9-]{1,20}")),
    TAX_IDENTIFICATION_NUMBER("C", "TAX IDENTIFICATION NUMBER - TIN", Pattern.compile("[A-Z0-9-]{1,20}")),
    IDENTIFICATION_NUMBER("D", "IDENTIFICATION NUMBER - IN", Pattern.compile("[A-Z0-9-]{1,20}"));

    private final String code;
    private final String description;
    private final Pattern numberPattern;

    SunatIdentityDocumentType(String code, String description, Pattern numberPattern) {
        this.code = code;
        this.description = description;
        this.numberPattern = numberPattern;
    }

    public String code() { return code; }
    public String description() { return description; }

    public boolean validNumberFormat(String number) {
        if (number == null || number.isBlank()) return false;
        return numberPattern.matcher(number.trim().toUpperCase()).matches();
    }

    public static SunatIdentityDocumentType fromCode(String code) {
        return Arrays.stream(values()).filter(value -> value.code.equals(code)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "Tipo de documento de identidad SUNAT no reconocido: " + code));
    }
}
