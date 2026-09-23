package pe.com.perubilling.issuer.application;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class RucValidatorTest {
    private final RucValidator validator = new RucValidator();

    @Test void acceptsValidCheckDigit() { assertTrue(validator.isValid("20123456786")); }
    @Test void rejectsInvalidCheckDigit() { assertFalse(validator.isValid("20123456780")); }
    @Test void rejectsMalformedValues() { assertFalse(validator.isValid(null)); assertFalse(validator.isValid("123")); }
}
