package pe.com.perubilling.shared.sunat.catalog;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class SunatCatalogModelTest {
    @Test
    void catalog06RecognizesAllModeledIdentityCodes() {
        for (String code : new String[] {"0", "1", "4", "6", "7", "A", "B", "C", "D"}) {
            assertEquals(code, SunatIdentityDocumentType.fromCode(code).code());
        }
        assertTrue(SunatIdentityDocumentType.DNI.validNumberFormat("12345678"));
        assertFalse(SunatIdentityDocumentType.DNI.validNumberFormat("123"));
    }

    @Test
    void operationCatalogSeparatesKnownFromCoreEnabledOperations() {
        assertTrue(SunatOperationType.fromCode("0101").coreSupported());
        assertTrue(SunatOperationType.fromCode("0107").coreSupported());
        assertTrue(SunatOperationType.fromCode("0102").coreSupported());
        assertFalse(SunatOperationType.fromCode("1001").coreSupported());
    }

    @Test
    void catalog53DistinguishesScopeAndTaxBaseEffect() {
        var lineDiscount = SunatAllowanceChargeReason.fromCode("00");
        var lineCharge = SunatAllowanceChargeReason.fromCode("48");
        var globalDiscount = SunatAllowanceChargeReason.fromCode("03");
        var globalCharge = SunatAllowanceChargeReason.fromCode("50");

        assertEquals(SunatAllowanceChargeReason.Scope.ITEM, lineDiscount.scope());
        assertTrue(lineDiscount.affectsTaxBase());
        assertEquals(SunatAllowanceChargeReason.Scope.ITEM, lineCharge.scope());
        assertFalse(lineCharge.affectsTaxBase());
        assertEquals(SunatAllowanceChargeReason.Scope.GLOBAL, globalDiscount.scope());
        assertEquals(SunatAllowanceChargeReason.Scope.GLOBAL, globalCharge.scope());
        assertTrue(lineCharge.coreSupported());
        assertTrue(globalCharge.coreSupported());
    }

    @Test
    void taxAndPriceCatalogsExposeCoreSupportExplicitly() {
        assertTrue(SunatTaxScheme.EXPORT.coreSupported());
        assertFalse(SunatTaxScheme.ISC.coreSupported());
        assertTrue(SunatPriceType.UNIT_PRICE.coreSupported());
        assertTrue(SunatPriceType.REFERENCE_VALUE.coreSupported());
    }

    @Test
    void coreUnitCatalogRejectsUnknownCodes() {
        assertEquals("NIU", SunatUnitCode.fromCode("NIU").code());
        assertEquals("ZZ", SunatUnitCode.fromCode("zz").code());
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
                () -> SunatUnitCode.fromCode("XYZ"));
    }
}
