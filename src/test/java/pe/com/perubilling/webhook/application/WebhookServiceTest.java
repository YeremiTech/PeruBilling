package pe.com.perubilling.webhook.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import pe.com.perubilling.outbox.application.OutboxPublisher;
import pe.com.perubilling.shared.crypto.SecretCryptoService;
import pe.com.perubilling.shared.domain.BusinessException;
import pe.com.perubilling.shared.security.TenantContext;
import pe.com.perubilling.webhook.api.CreateWebhookRequest;
import pe.com.perubilling.webhook.domain.WebhookEndpointEntity;
import pe.com.perubilling.webhook.infrastructure.WebhookEndpointRepository;

class WebhookServiceTest {
    @Test
    void createGeneratesOneTimeSigningSecretAndStoresOnlyEncryptedValue() {
        var endpoints = mock(WebhookEndpointRepository.class);
        var tenant = mock(TenantContext.class);
        var crypto = mock(SecretCryptoService.class);
        var validator = mock(SafeWebhookUrlValidator.class);
        var outbox = mock(OutboxPublisher.class);
        UUID tenantId = UUID.randomUUID();
        when(tenant.requireTenantId()).thenReturn(tenantId);
        when(crypto.encrypt(anyString())).thenReturn("encrypted-secret");
        var service = new WebhookService(endpoints, tenant, crypto, validator, outbox);
        var request = new CreateWebhookRequest(
                "https://hooks.example.com/perubilling",
                Set.of("document.rejected", "document.accepted"));

        var response = service.create(request);

        var captor = ArgumentCaptor.forClass(WebhookEndpointEntity.class);
        verify(endpoints).save(captor.capture());
        var stored = captor.getValue();
        assertEquals(tenantId, stored.getTenantId());
        assertEquals("encrypted-secret", stored.getSecretEncrypted());
        assertEquals("document.accepted,document.rejected", stored.getEventTypes());
        assertTrue(response.signingSecret().startsWith("whsec_"));
        verify(validator).validate("https://hooks.example.com/perubilling");
    }

    @Test
    void createRejectsUnsupportedEventsBeforePersistence() {
        var endpoints = mock(WebhookEndpointRepository.class);
        var tenant = mock(TenantContext.class);
        var crypto = mock(SecretCryptoService.class);
        var validator = mock(SafeWebhookUrlValidator.class);
        var outbox = mock(OutboxPublisher.class);
        var service = new WebhookService(endpoints, tenant, crypto, validator, outbox);

        assertThrows(BusinessException.class, () -> service.create(new CreateWebhookRequest(
                "https://hooks.example.com/perubilling", Set.of("document.unknown"))));

        verify(endpoints, never()).save(org.mockito.ArgumentMatchers.any());
    }
}
