package pe.com.perubilling.access.security;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import pe.com.perubilling.access.domain.ApiKeyEntity;
import pe.com.perubilling.access.infrastructure.ApiKeyRepository;
import pe.com.perubilling.shared.crypto.HashingService;
import pe.com.perubilling.tenant.domain.TenantEntity;
import pe.com.perubilling.tenant.domain.TenantStatus;
import pe.com.perubilling.tenant.infrastructure.TenantRepository;

class ApiKeyAuthenticationFilterTest {
    @AfterEach
    void clearSecurity() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void authenticationTouchesUsageWithThrottledUpdateInsteadOfSavingWholeEntity() throws Exception {
        var repository = mock(ApiKeyRepository.class);
        var tenants = mock(TenantRepository.class);
        var hashing = mock(HashingService.class);
        UUID keyId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        var key = new ApiKeyEntity();
        key.setId(keyId);
        key.setTenantId(tenantId);
        key.setName("erp");
        key.setScopes("DOCUMENT_READ");
        key.setExpiresAt(Instant.now().plusSeconds(3600));
        var tenant = new TenantEntity();
        tenant.setStatus(TenantStatus.ACTIVE);

        when(hashing.sha256("secret:test-api-key-pepper-32-characters-minimum")).thenReturn("hash");
        when(repository.findBySecretHashAndRevokedAtIsNull("hash")).thenReturn(Optional.of(key));
        when(tenants.findById(tenantId)).thenReturn(Optional.of(tenant));

        var filter = new ApiKeyAuthenticationFilter(repository, tenants, hashing, "test-api-key-pepper-32-characters-minimum", 300);
        var request = new MockHttpServletRequest("GET", "/api/v1/documents");
        request.addHeader("X-API-Key", "secret");
        var response = new MockHttpServletResponse();
        filter.doFilter(request, response, (req, res) -> {});

        assertNotNull(SecurityContextHolder.getContext().getAuthentication());
        verify(repository).touchLastUsedAt(eq(keyId), any(Instant.class), any(Instant.class));
        verify(repository, never()).save(key);
    }
}
