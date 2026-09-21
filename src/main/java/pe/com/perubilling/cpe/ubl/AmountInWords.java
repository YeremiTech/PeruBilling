package pe.com.perubilling.cpe.ubl;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Currency;
import java.util.Locale;
import org.springframework.stereotype.Component;

@Component
public class AmountInWords {
    private static final String[] UNITS = {
        "", "UNO", "DOS", "TRES", "CUATRO", "CINCO", "SEIS", "SIETE", "OCHO", "NUEVE",
        "DIEZ", "ONCE", "DOCE", "TRECE", "CATORCE", "QUINCE", "DIECISEIS", "DIECISIETE",
        "DIECIOCHO", "DIECINUEVE", "VEINTE", "VEINTIUNO", "VEINTIDOS", "VEINTITRES",
        "VEINTICUATRO", "VEINTICINCO", "VEINTISEIS", "VEINTISIETE", "VEINTIOCHO", "VEINTINUEVE"
    };
    private static final String[] TENS = {"", "", "", "TREINTA", "CUARENTA", "CINCUENTA", "SESENTA", "SETENTA", "OCHENTA", "NOVENTA"};
    private static final String[] HUNDREDS = {"", "CIENTO", "DOSCIENTOS", "TRESCIENTOS", "CUATROCIENTOS", "QUINIENTOS", "SEISCIENTOS", "SETECIENTOS", "OCHOCIENTOS", "NOVECIENTOS"};

    public String convert(BigDecimal amount, String currency) {
        BigDecimal scaled = amount.setScale(2, RoundingMode.HALF_UP);
        long whole = scaled.longValue();
        int cents = scaled.remainder(BigDecimal.ONE).movePointRight(2).abs().intValue();
        String currencyName = currencyName(currency);
        return words(whole).replace("UNO MIL", "UN MIL") + " CON " + String.format("%02d", cents) + "/100 " + currencyName;
    }

    private String currencyName(String code) {
        if (code == null || code.isBlank()) throw new IllegalArgumentException("Moneda requerida");
        String normalized = code.trim().toUpperCase(Locale.ROOT);
        if ("PEN".equals(normalized)) return "SOLES";
        if ("USD".equals(normalized)) return "DOLARES AMERICANOS";
        Currency currency = Currency.getInstance(normalized);
        return currency.getDisplayName(Locale.forLanguageTag("es-PE")).toUpperCase(Locale.ROOT);
    }

    private String words(long n) {
        if (n == 0) return "CERO";
        if (n < 0) return "MENOS " + words(-n);
        if (n < 30) return UNITS[(int)n];
        if (n < 100) return TENS[(int)n / 10] + (n % 10 == 0 ? "" : " Y " + words(n % 10));
        if (n == 100) return "CIEN";
        if (n < 1000) return HUNDREDS[(int)n / 100] + (n % 100 == 0 ? "" : " " + words(n % 100));
        if (n < 2000) return "MIL" + (n % 1000 == 0 ? "" : " " + words(n % 1000));
        if (n < 1_000_000) return words(n / 1000) + " MIL" + (n % 1000 == 0 ? "" : " " + words(n % 1000));
        if (n < 2_000_000) return "UN MILLON" + (n % 1_000_000 == 0 ? "" : " " + words(n % 1_000_000));
        if (n < 1_000_000_000) return words(n / 1_000_000) + " MILLONES" + (n % 1_000_000 == 0 ? "" : " " + words(n % 1_000_000));
        return Long.toString(n);
    }
}
