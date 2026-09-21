package pe.com.perubilling.issuer.application;

import org.springframework.stereotype.Component;

@Component
public class RucValidator {
    private static final int[] WEIGHTS = {5,4,3,2,7,6,5,4,3,2};

    public boolean isValid(String ruc) {
        if (ruc == null || !ruc.matches("\\d{11}")) return false;
        int sum = 0;
        for (int i = 0; i < 10; i++) sum += Character.digit(ruc.charAt(i), 10) * WEIGHTS[i];
        int expected = 11 - (sum % 11);
        if (expected == 10) expected = 0;
        else if (expected == 11) expected = 1;
        return expected == Character.digit(ruc.charAt(10), 10);
    }
}
