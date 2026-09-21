package pe.com.perubilling.billing.api;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;

import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import pe.com.perubilling.billing.application.DocumentService;
import pe.com.perubilling.shared.domain.BusinessException;
import pe.com.perubilling.shared.security.ApiKeyPrincipal;

class DocumentControllerSecurityContractTest {
    @Test
    void machineToMachineEmissionRequiresIdempotencyKeyAtApiBoundary() {
        var service = mock(DocumentService.class);
        var controller = new DocumentController(service);
        var principal = new ApiKeyPrincipal(UUID.randomUUID(), UUID.randomUUID(), "erp", Set.of("DOCUMENT_WRITE"));
        var authentication = new UsernamePasswordAuthenticationToken(principal, null, Set.of());

        assertThrows(BusinessException.class,
                () -> controller.createInvoice(null, null, authentication));
    }
}
