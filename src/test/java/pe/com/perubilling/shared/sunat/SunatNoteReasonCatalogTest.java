package pe.com.perubilling.shared.sunat;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import pe.com.perubilling.shared.domain.DocumentType;

class SunatNoteReasonCatalogTest {
    @Test
    void validatesSupportedCreditAndDebitReasons() {
        assertThat(SunatNoteReasonCatalog.isSupported(DocumentType.CREDIT_NOTE, "01", "01")).isTrue();
        assertThat(SunatNoteReasonCatalog.isSupported(DocumentType.CREDIT_NOTE, "09", "01")).isTrue();
        assertThat(SunatNoteReasonCatalog.isSupported(DocumentType.DEBIT_NOTE, "01", "01")).isTrue();
        assertThat(SunatNoteReasonCatalog.isSupported(DocumentType.DEBIT_NOTE, "02", "01")).isTrue();
        assertThat(SunatNoteReasonCatalog.isSupported(DocumentType.DEBIT_NOTE, "03", "01")).isFalse();
    }

    @Test
    void disallowsUnsupportedCreditReasonsForReceiptReferences() {
        assertThat(SunatNoteReasonCatalog.isSupported(DocumentType.CREDIT_NOTE, "04", "03")).isFalse();
        assertThat(SunatNoteReasonCatalog.isSupported(DocumentType.CREDIT_NOTE, "05", "03")).isFalse();
        assertThat(SunatNoteReasonCatalog.isSupported(DocumentType.CREDIT_NOTE, "08", "03")).isFalse();
        assertThat(SunatNoteReasonCatalog.isSupported(DocumentType.CREDIT_NOTE, "01", "03")).isTrue();
    }
}
