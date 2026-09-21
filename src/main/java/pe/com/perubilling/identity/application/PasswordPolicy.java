package pe.com.perubilling.identity.application;

import org.springframework.stereotype.Component;
import pe.com.perubilling.shared.domain.BusinessException;

@Component
public class PasswordPolicy {
    public void validate(String password, String email) {
        if (password == null || password.length() < 12 || password.length() > 128) {
            throw BusinessException.badRequest("WEAK_PASSWORD", "La contraseña debe tener entre 12 y 128 caracteres");
        }
        boolean lower=false, upper=false, digit=false, symbol=false;
        for (char c : password.toCharArray()) {
            lower |= Character.isLowerCase(c);
            upper |= Character.isUpperCase(c);
            digit |= Character.isDigit(c);
            symbol |= !Character.isLetterOrDigit(c) && !Character.isWhitespace(c);
        }
        if (!(lower && upper && digit && symbol)) {
            throw BusinessException.badRequest("WEAK_PASSWORD", "La contraseña debe incluir mayúscula, minúscula, número y símbolo");
        }
        String local = email == null ? "" : email.split("@",2)[0].toLowerCase();
        if (local.length() >= 4 && password.toLowerCase().contains(local)) {
            throw BusinessException.badRequest("WEAK_PASSWORD", "La contraseña no debe contener el identificador del correo");
        }
    }
}
