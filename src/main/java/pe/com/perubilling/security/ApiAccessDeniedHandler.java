package pe.com.perubilling.security;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;
import pe.com.perubilling.shared.api.ApiErrorResponseWriter;

@Component
public class ApiAccessDeniedHandler implements AccessDeniedHandler {
    private final ApiErrorResponseWriter writer;

    public ApiAccessDeniedHandler(ApiErrorResponseWriter writer) {
        this.writer = writer;
    }

    @Override
    public void handle(
            HttpServletRequest request,
            HttpServletResponse response,
            AccessDeniedException accessDeniedException) throws IOException, ServletException {
        writer.write(request, response, HttpStatus.FORBIDDEN, "FORBIDDEN", "No tiene permisos para realizar esta operación");
    }
}
