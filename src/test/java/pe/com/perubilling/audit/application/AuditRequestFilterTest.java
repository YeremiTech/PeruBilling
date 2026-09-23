package pe.com.perubilling.audit.application;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import pe.com.perubilling.audit.infrastructure.AuditEventRepository;
import pe.com.perubilling.shared.security.TenantContext;

class AuditRequestFilterTest {
    @AfterEach
    void clearSecurity() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void auditPersistenceFailureIsObservableWithoutBreakingResponse() {
        var repository = mock(AuditEventRepository.class);
        var tenantContext = mock(TenantContext.class);
        var registry = new SimpleMeterRegistry();
        when(tenantContext.requireTenantId()).thenReturn(UUID.randomUUID());
        when(repository.save(any())).thenThrow(new IllegalStateException("db unavailable"));
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated("admin", null, java.util.List.of()));

        var filter = new AuditRequestFilter(repository, tenantContext, registry);
        var request = new MockHttpServletRequest("POST", "/api/v1/documents");
        var response = new MockHttpServletResponse();
        response.setHeader("X-Request-Id", "req-1");

        assertDoesNotThrow(() -> filter.doFilter(request, response, (req, res) -> {}));
        assertEquals(1.0, registry.get("perubilling.audit.write_failures").counter().count());
    }
}
