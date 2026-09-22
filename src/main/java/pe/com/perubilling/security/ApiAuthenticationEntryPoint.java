package pe.com.perubilling.security;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;
import pe.com.perubilling.shared.api.ApiErrorResponseWriter;

@Component
public class ApiAuthenticationEntryPoint implements AuthenticationEntryPoint {
    private final ApiErrorResponseWriter writer;

    public ApiAuthenticationEntryPoint(ApiErrorResponseWriter writer) {
        this.writer = writer;
    }

    @Override
    public void commence(
            HttpServletRequest request,
            HttpServletResponse response,
            AuthenticationException authException) throws IOException, ServletException {
        writer.write(request, response, HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "Autenticación requerida");
    }
}
