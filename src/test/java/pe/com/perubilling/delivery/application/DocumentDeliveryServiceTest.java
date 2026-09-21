package pe.com.perubilling.delivery.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import pe.com.perubilling.billing.domain.ElectronicDocumentEntity;
import pe.com.perubilling.billing.infrastructure.ElectronicDocumentRepository;
import pe.com.perubilling.delivery.domain.DocumentAccessTokenEntity;
import pe.com.perubilling.delivery.infrastructure.DocumentAccessTokenRepository;
import pe.com.perubilling.issuer.infrastructure.IssuerRepository;
import pe.com.perubilling.shared.crypto.HashingService;
import pe.com.perubilling.shared.security.TenantContext;
import pe.com.perubilling.shared.storage.ArtifactStorage;

class DocumentDeliveryServiceTest {
    @Test
    void accessLinkStoresOnlyTokenHashAndEnforcesAtLeastOneYearValidity() {
        var documents = mock(ElectronicDocumentRepository.class);
        var tokens = mock(DocumentAccessTokenRepository.class);
        var issuers = mock(IssuerRepository.class);
        var tenant = mock(TenantContext.class);
        var hashing = mock(HashingService.class);
        var storage = mock(ArtifactStorage.class);
        UUID tenantId = UUID.randomUUID();
        UUID documentId = UUID.randomUUID();
        var document = mock(ElectronicDocumentEntity.class);
        when(tenant.requireTenantId()).thenReturn(tenantId);
        when(documents.findByIdAndTenantId(documentId, tenantId)).thenReturn(Optional.of(document));
        when(document.getPdfPath()).thenReturn("tenant/document/invoice.pdf");
        when(hashing.sha256(anyString())).thenReturn("hashed-token");
        var service = new DocumentDeliveryService(
                documents, tokens, issuers, tenant, hashing, storage, "https://billing.example.com/");

        Instant before = Instant.now();
        var response = service.createAccessLink(documentId, 30);

        var captor = ArgumentCaptor.forClass(DocumentAccessTokenEntity.class);
        verify(tokens).save(captor.capture());
        var stored = captor.getValue();
        assertEquals(tenantId, stored.getTenantId());
        assertEquals(documentId, stored.getDocumentId());
        assertEquals("hashed-token", stored.getTokenHash());
        assertNotEquals("hashed-token", response.token());
        assertTrue(response.url().startsWith("https://billing.example.com/public/v1/documents/"));
        assertTrue(stored.getExpiresAt().isAfter(before.plusSeconds(364L * 24 * 60 * 60)));
    }
}
