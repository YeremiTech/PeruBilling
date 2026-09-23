package pe.com.perubilling.identity.application;

import java.time.Instant;
import org.springframework.stereotype.Service;
import pe.com.perubilling.identity.api.LoginRequest;
import pe.com.perubilling.identity.api.LoginResponse;
import pe.com.perubilling.shared.domain.BusinessException;

@Service
public class AuthService {
    private final AuthenticationStateService authenticationState;
    private final JwtTokenService tokenService;

    public AuthService(AuthenticationStateService authenticationState, JwtTokenService tokenService) {
        this.authenticationState = authenticationState;
        this.tokenService = tokenService;
    }

    public LoginResponse login(LoginRequest request) {
        var result = authenticationState.authenticate(
                request.email().trim(), request.password(), Instant.now());

        if (result.outcome() == AuthenticationStateService.Outcome.LOCKED) {
            throw BusinessException.tooManyRequests(
                    "ACCOUNT_TEMPORARILY_LOCKED",
                    "Demasiados intentos fallidos. Intente nuevamente más tarde");
        }
        if (result.outcome() != AuthenticationStateService.Outcome.SUCCESS || result.user() == null) {
            throw invalidCredentials();
        }

        var user = result.user();
        var token = tokenService.issue(user);
        return new LoginResponse(
                token.value(), "Bearer", token.expiresAt(),
                user.getTenantId(), user.getRole().name());
    }

    private BusinessException invalidCredentials() {
        return BusinessException.unauthorized(
                "INVALID_CREDENTIALS",
                "Credenciales inválidas");
    }
}
