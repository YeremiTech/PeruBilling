package pe.com.perubilling.shared.sunat;

import java.util.List;
import pe.com.perubilling.shared.domain.DocumentType;

public final class SunatNoteReasonCatalog {
    private static final List<Reason> CREDIT_NOTE_REASONS = List.of(
            new Reason("01", "Anulación de la operación", true),
            new Reason("02", "Anulación por error en el RUC", true),
            new Reason("03", "Corrección por error en la descripción", true),
            new Reason("04", "Descuento global", false),
            new Reason("05", "Descuento por ítem", false),
            new Reason("06", "Devolución total", true),
            new Reason("07", "Devolución por ítem", true),
            new Reason("08", "Bonificación", false),
            new Reason("09", "Disminución en el valor", true));
    private static final List<Reason> DEBIT_NOTE_REASONS = List.of(
            new Reason("01", "Intereses por mora", true),
            new Reason("02", "Aumento en el valor", true));

    private SunatNoteReasonCatalog() {}

    public record Reason(String code, String description, boolean allowedForReceipt) {}

    public static List<Reason> creditReasons() { return CREDIT_NOTE_REASONS; }
    public static List<Reason> debitReasons() { return DEBIT_NOTE_REASONS; }

    public static boolean isSupported(DocumentType noteType, String reasonCode, String referencedDocumentType) {
        if (reasonCode == null) return false;
        if (noteType == DocumentType.CREDIT_NOTE) {
            return CREDIT_NOTE_REASONS.stream().anyMatch(reason -> reason.code().equals(reasonCode)
                    && (!"03".equals(referencedDocumentType) || reason.allowedForReceipt()));
        }
        if (noteType == DocumentType.DEBIT_NOTE) {
            return DEBIT_NOTE_REASONS.stream().anyMatch(reason -> reason.code().equals(reasonCode));
        }
        return false;
    }

    public static String supportedDescription(DocumentType noteType, String referencedDocumentType) {
        if (noteType == DocumentType.CREDIT_NOTE && "03".equals(referencedDocumentType)) {
            return "NC sobre boleta: " + CREDIT_NOTE_REASONS.stream()
                    .filter(Reason::allowedForReceipt).map(Reason::code).reduce((a, b) -> a + "," + b).orElse("");
        }
        if (noteType == DocumentType.CREDIT_NOTE) {
            return "NC: " + CREDIT_NOTE_REASONS.stream().map(Reason::code).reduce((a, b) -> a + "," + b).orElse("");
        }
        if (noteType == DocumentType.DEBIT_NOTE) {
            return "ND: " + DEBIT_NOTE_REASONS.stream().map(Reason::code).reduce((a, b) -> a + "," + b).orElse("");
        }
        return "No aplica";
    }
}
