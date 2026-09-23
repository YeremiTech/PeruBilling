package pe.com.perubilling.cpe.ubl;

import static org.junit.jupiter.api.Assertions.assertTrue;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class AmountInWordsTest {
    @Test void rendersAmountInSoles() {
        String value = new AmountInWords().convert(new BigDecimal("118.00"), "PEN");
        assertTrue(value.contains("118") || value.toUpperCase().contains("CIENTO"));
    }

    @org.junit.jupiter.api.Test
    void supportsEuroWithoutCallingItSoles() {
        String value = new AmountInWords().convert(new java.math.BigDecimal("125.50"), "EUR");
        org.junit.jupiter.api.Assertions.assertTrue(value.contains("EURO"));
        org.junit.jupiter.api.Assertions.assertFalse(value.endsWith("SOLES"));
    }
}
