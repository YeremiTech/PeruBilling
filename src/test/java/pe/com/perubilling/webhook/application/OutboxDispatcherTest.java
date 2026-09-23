package pe.com.perubilling.webhook.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import pe.com.perubilling.outbox.domain.OutboxEventEntity;
import pe.com.perubilling.outbox.infrastructure.OutboxEventRepository;
import pe.com.perubilling.webhook.domain.WebhookDeliveryEntity;
import pe.com.perubilling.webhook.domain.WebhookEndpointEntity;
import pe.com.perubilling.webhook.infrastructure.WebhookDeliveryRepository;
import pe.com.perubilling.webhook.infrastructure.WebhookEndpointRepository;

class OutboxDispatcherTest {
    @Test
    void createsOneDeliveryForSubscribedEndpointAndPublishesEvent() {
        var events = mock(OutboxEventRepository.class);
        var endpoints = mock(WebhookEndpointRepository.class);
        var deliveries = mock(WebhookDeliveryRepository.class);
        var dispatcher = new OutboxDispatcher(events, endpoints, deliveries);

        UUID eventId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        UUID documentId = UUID.randomUUID();
        var event = event(eventId, tenantId, documentId, "document.accepted");
        var endpoint = endpoint(UUID.randomUUID(), tenantId, "document.accepted,document.rejected");

        when(events.findById(eventId)).thenReturn(Optional.of(event));
        when(endpoints.findAllByTenantIdAndActiveTrue(tenantId)).thenReturn(List.of(endpoint));
        when(deliveries.existsBySourceEventIdAndEndpointId(eventId, endpoint.getId())).thenReturn(false);

        dispatcher.dispatch(eventId);

        var captor = ArgumentCaptor.forClass(WebhookDeliveryEntity.class);
        verify(deliveries).save(captor.capture());
        var delivery = captor.getValue();
        assertEquals(tenantId, delivery.getTenantId());
        assertEquals(endpoint.getId(), delivery.getEndpointId());
        assertEquals(eventId, delivery.getSourceEventId());
        assertEquals(documentId, delivery.getResourceId());
        assertEquals("document.accepted", delivery.getEventType());
        assertNotNull(delivery.getEventId());
        assertEquals("PUBLISHED", event.getStatus());
        assertNotNull(event.getPublishedAt());
    }

    @Test
    void doesNotDuplicateExistingDelivery() {
        var events = mock(OutboxEventRepository.class);
        var endpoints = mock(WebhookEndpointRepository.class);
        var deliveries = mock(WebhookDeliveryRepository.class);
        var dispatcher = new OutboxDispatcher(events, endpoints, deliveries);

        UUID eventId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        var event = event(eventId, tenantId, UUID.randomUUID(), "document.accepted");
        var endpoint = endpoint(UUID.randomUUID(), tenantId, "document.accepted");
        when(events.findById(eventId)).thenReturn(Optional.of(event));
        when(endpoints.findAllByTenantIdAndActiveTrue(tenantId)).thenReturn(List.of(endpoint));
        when(deliveries.existsBySourceEventIdAndEndpointId(eventId, endpoint.getId())).thenReturn(true);

        dispatcher.dispatch(eventId);

        verify(deliveries, never()).save(org.mockito.ArgumentMatchers.any());
        assertEquals("PUBLISHED", event.getStatus());
    }

    @Test
    void repeatedFailureMovesEventToFailedAfterTenAttempts() {
        var events = mock(OutboxEventRepository.class);
        var endpoints = mock(WebhookEndpointRepository.class);
        var deliveries = mock(WebhookDeliveryRepository.class);
        var dispatcher = new OutboxDispatcher(events, endpoints, deliveries);
        UUID eventId = UUID.randomUUID();
        var event = event(eventId, UUID.randomUUID(), UUID.randomUUID(), "document.failed");
        event.setAttemptCount(9);
        when(events.findById(eventId)).thenReturn(Optional.of(event));

        dispatcher.fail(eventId, "failure");

        assertEquals(10, event.getAttemptCount());
        assertEquals("FAILED", event.getStatus());
        assertEquals(null, event.getNextAttemptAt());
    }

    private OutboxEventEntity event(UUID id, UUID tenantId, UUID aggregateId, String type) {
        var event = new OutboxEventEntity();
        event.setId(id);
        event.setTenantId(tenantId);
        event.setAggregateId(aggregateId);
        event.setAggregateType("electronic_document");
        event.setEventType(type);
        event.setPayload("{}");
        return event;
    }

    private WebhookEndpointEntity endpoint(UUID id, UUID tenantId, String eventTypes) {
        var endpoint = new WebhookEndpointEntity();
        endpoint.setId(id);
        endpoint.setTenantId(tenantId);
        endpoint.setUrl("https://hooks.example.com/perubilling");
        endpoint.setEventTypes(eventTypes);
        endpoint.setSecretEncrypted("encrypted");
        endpoint.setActive(true);
        return endpoint;
    }
}
