package pe.com.perubilling.audit.application;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import pe.com.perubilling.audit.domain.AuditEventEntity;
import pe.com.perubilling.audit.infrastructure.AuditEventRepository;
import pe.com.perubilling.shared.security.TenantContext;

@Component
public class AuditRequestFilter extends OncePerRequestFilter {
    private static final Logger log = LoggerFactory.getLogger(AuditRequestFilter.class);
    private static final Set<String> MUTATING_METHODS = Set.of("POST", "PUT", "PATCH", "DELETE");
    private static final String LOGIN_PATH = "/api/v1/auth/login";

    private final AuditEventRepository repository;
    private final TenantContext tenantContext;
    private final Counter auditWriteFailures;

    public AuditRequestFilter(AuditEventRepository repository, TenantContext tenantContext, MeterRegistry meterRegistry) {
        this.repository = repository;
        this.tenantContext = tenantContext;
        this.auditWriteFailures = Counter.builder("perubilling.audit.write_failures")
                .description("Cantidad de eventos HTTP mutantes que no pudieron persistirse en auditoría")
                .register(meterRegistry);
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain chain) throws ServletException, IOException {
        try {
            chain.doFilter(request, response);
        } finally {
            auditMutation(request, response);
        }
    }

    private void auditMutation(HttpServletRequest request, HttpServletResponse response) {
        if (!MUTATING_METHODS.contains(request.getMethod()) || LOGIN_PATH.equals(request.getRequestURI())) {
            return;
        }
        try {
            var authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication == null || !authentication.isAuthenticated()) {
                return;
            }
            var event = new AuditEventEntity();
            try {
                event.setTenantId(tenantContext.requireTenantId());
            } catch (RuntimeException ignored) {
                log.debug("Request mutante autenticado sin tenant disponible para auditoría: {} {}",
                        request.getMethod(), request.getRequestURI());
            }
            event.setActor(authentication.getName());
            event.setHttpMethod(request.getMethod());
            event.setRequestPath(request.getRequestURI());
            event.setResponseStatus(response.getStatus());
            event.setRequestId(response.getHeader("X-Request-Id"));
            event.setRemoteAddress(request.getRemoteAddr());
            repository.save(event);
        } catch (RuntimeException exception) {
            auditWriteFailures.increment();
            log.error(
                    "No se pudo persistir auditoría HTTP para {} {} requestId={}",
                    request.getMethod(),
                    request.getRequestURI(),
                    response.getHeader("X-Request-Id"),
                    exception);
        }
    }
}
