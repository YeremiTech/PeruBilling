package pe.com.perubilling.shared.sunat.catalog;

import java.util.Arrays;

/** Catálogo CORE de unidades de medida admitidas por PeruBilling. */
public enum SunatUnitCode {
    BJ("BALDE"), BG("BOLSA"), BO("BOTELLAS"), BX("CAJA"), CT("CARTONES"), CY("CILINDRO"),
    CJ("CONOS"), GRM("GRAMO"), SET("JUEGO"), KGM("KILOGRAMO"), KTM("KILOMETRO"), KT("KIT"),
    CA("LATAS"), LBR("LIBRAS"), LTR("LITRO"), MTR("METRO"), MLL("MILLARES"),
    UM("MILLON DE UNIDADES"), NIU("UNIDAD (BIENES)"), ZZ("UNIDAD (SERVICIOS)"),
    DZN("DOCENA"), HUR("HORA"), MIN("MINUTO"), SEC("SEGUNDO"), DAY("DIA"),
    MTK("METRO CUADRADO"), MTQ("METRO CUBICO"), TNE("TONELADA"),
    MGM("MILIGRAMO"), MLT("MILILITRO"), GLL("GALON"), PR("PAR"),
    PK("PAQUETE"), CS("CAJA / CASE"), EA("UNIDAD / EACH"), RO("ROLLO");

    private final String description;

    SunatUnitCode(String description) { this.description = description; }
    public String code() { return name(); }
    public String description() { return description; }

    public static SunatUnitCode fromCode(String code) {
        if (code == null) throw new IllegalArgumentException("Unidad de medida SUNAT requerida");
        return Arrays.stream(values())
                .filter(value -> value.name().equalsIgnoreCase(code.trim()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unidad de medida no reconocida en el catálogo incluido: " + code));
    }
}
