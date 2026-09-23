package pe.com.perubilling.billing.tax;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Test;
import pe.com.perubilling.billing.domain.TaxAffectation;

class TaxAffectationTest {
    @Test
    void preservesRealTaxSchemeForFreeLines() {
        assertEquals("1000", TaxAffectation.fromCode("11").taxSchemeId());
        assertEquals("IGV", TaxAffectation.fromCode("11").taxSchemeName());
        assertEquals("9997", TaxAffectation.fromCode("21").taxSchemeId());
        assertEquals("9998", TaxAffectation.fromCode("35").taxSchemeId());
    }

    @Test
    void mapsIvapIndependentlyFromIgv() {
        assertEquals("1016", TaxAffectation.fromCode("17").taxSchemeId());
        assertEquals("IVAP", TaxAffectation.fromCode("17").taxSchemeName());
        assertEquals("VAT", TaxAffectation.fromCode("17").taxTypeCode());
    }
}
