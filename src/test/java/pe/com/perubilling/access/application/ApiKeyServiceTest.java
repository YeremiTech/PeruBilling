package pe.com.perubilling.access.application;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import pe.com.perubilling.access.api.CreateApiKeyRequest;
import pe.com.perubilling.access.infrastructure.ApiKeyRepository;
import pe.com.perubilling.shared.crypto.HashingService;
import pe.com.perubilling.shared.domain.BusinessException;
import pe.com.perubilling.shared.security.TenantContext;

class ApiKeyServiceTest {
    @Test
    void operationsReadIsAnAllowedMachineScope() {
        var repository = mock(ApiKeyRepository.class);
        var tenantContext = mock(TenantContext.class);
        var hashing = mock(HashingService.class);
        when(tenantContext.requireTenantId()).thenReturn(UUID.randomUUID());
        when(hashing.sha256(anyString())).thenReturn("hash");
        var service = new ApiKeyService(repository, tenantContext, hashing, "test-api-key-pepper-32-characters-minimum");

        assertDoesNotThrow(() -> service.create(new CreateApiKeyRequest("prometheus", Set.of("OPERATIONS_READ"), null)));
    }

    @Test
    void unknownScopeRemainsRejected() {
        var repository = mock(ApiKeyRepository.class);
        var tenantContext = mock(TenantContext.class);
        var hashing = mock(HashingService.class);
        when(tenantContext.requireTenantId()).thenReturn(UUID.randomUUID());
        var service = new ApiKeyService(repository, tenantContext, hashing, "test-api-key-pepper-32-characters-minimum");

        assertThrows(BusinessException.class,
                () -> service.create(new CreateApiKeyRequest("bad", Set.of("ROOT"), null)));
    }
    @Test
    void weakPepperIsRejectedAtStartup() {
        var repository = mock(ApiKeyRepository.class);
        var tenantContext = mock(TenantContext.class);
        var hashing = mock(HashingService.class);
        assertThrows(IllegalStateException.class,
                () -> new ApiKeyService(repository, tenantContext, hashing, "too-short"));
    }

}
