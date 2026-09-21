package pe.com.perubilling.shared.sunat.catalog;

/** Esquemas tributarios UBL usados/conocidos por PeruBilling. */
public enum SunatTaxScheme {
    IGV("1000", "IGV", true),
    ISC("2000", "ISC", false),
    ICBPER("7152", "ICBPER", true),
    EXPORT("9995", "Exportación", true),
    FREE("9996", "Gratuito", true),
    EXONERATED("9997", "Exonerado", true),
    UNAFFECTED("9998", "Inafecto", true),
    OTHER("9999", "Otros tributos", false),
    IVAP("1016", "IVAP", true);

    private final String code;
    private final String description;
    private final boolean coreSupported;

    SunatTaxScheme(String code, String description, boolean coreSupported) {
        this.code = code;
        this.description = description;
        this.coreSupported = coreSupported;
    }

    public String code() { return code; }
    public String description() { return description; }
    public boolean coreSupported() { return coreSupported; }
}
