package pe.com.perubilling.shared.sunat.catalog;

import java.util.Arrays;

/**
 * Catálogo de tipos de operación conocidos por el motor. Solo los marcados CORE
 * pueden emitirse mientras su perfil UBL y pruebas regulatorias estén calibrados.
 */
public enum SunatOperationType {
    INTERNAL_SALE("0101", "Venta Interna", true),
    EXPORT("0102", "Exportación", true),
    NON_DOMICILED("0103", "No Domiciliados", false),
    ADVANCE_PAYMENT("0104", "Venta Interna - Anticipos", false),
    ITINERANT_SALE("0105", "Venta Itinerante", false),
    INVOICE_GUIDE("0106", "Factura Guía", false),
    IVAP("0107", "Venta Arroz Pilado", true),
    PERCEPTION_INVOICE("0108", "Factura Comprobante de Percepción", false),
    REMITTER_GUIDE("0110", "Factura - Guía remitente", false),
    DETRACTION("1001", "Operación sujeta a detracción", false),
    DETRACTION_HYDROBIOLOGICAL("1002", "Detracción - recursos hidrobiológicos", false),
    DETRACTION_PASSENGER_TRANSPORT("1003", "Detracción - transporte de pasajeros", false),
    DETRACTION_CARGO_TRANSPORT("1004", "Detracción - transporte de carga", false);

    private final String code;
    private final String description;
    private final boolean coreSupported;

    SunatOperationType(String code, String description, boolean coreSupported) {
        this.code = code;
        this.description = description;
        this.coreSupported = coreSupported;
    }

    public String code() { return code; }
    public String description() { return description; }
    public boolean coreSupported() { return coreSupported; }

    public static SunatOperationType fromCode(String code) {
        return Arrays.stream(values()).filter(value -> value.code.equals(code)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Tipo de operación SUNAT no reconocido por esta versión: " + code));
    }
}
