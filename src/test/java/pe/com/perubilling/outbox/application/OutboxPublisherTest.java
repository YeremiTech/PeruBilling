package pe.com.perubilling.outbox.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import pe.com.perubilling.outbox.domain.OutboxEventEntity;
import pe.com.perubilling.outbox.infrastructure.OutboxEventRepository;
import tools.jackson.databind.json.JsonMapper;

class OutboxPublisherTest {
    @Test
    void publishesTransactionalEnvelopeWithoutLosingTenantOrAggregate() throws Exception {
        var repository = mock(OutboxEventRepository.class);
        var mapper = mock(JsonMapper.class);
        when(mapper.writeValueAsString(any())).thenReturn("{\"type\":\"document.accepted\"}");
        var publisher = new OutboxPublisher(repository, mapper);

        UUID tenantId = UUID.randomUUID();
        UUID documentId = UUID.randomUUID();
        UUID eventId = publisher.publish(
                tenantId,
                "document.accepted",
                "electronic_document",
                documentId,
                Map.of("documentId", documentId.toString()));

        var captor = ArgumentCaptor.forClass(OutboxEventEntity.class);
        verify(repository).save(captor.capture());
        var saved = captor.getValue();
        assertNotNull(eventId);

        assertNull(saved.getId());
        assertEquals(tenantId, saved.getTenantId());
        assertEquals(documentId, saved.getAggregateId());
        assertEquals("electronic_document", saved.getAggregateType());
        assertEquals("document.accepted", saved.getEventType());
        assertNotNull(saved.getPayload());
    }

    @Test
    void serializationFailureDoesNotSilentlyDropEvent() throws Exception {
        var repository = mock(OutboxEventRepository.class);
        var mapper = mock(JsonMapper.class);
        when(mapper.writeValueAsString(any())).thenThrow(new IllegalStateException("serialization failed"));
        var publisher = new OutboxPublisher(repository, mapper);

        assertThrows(IllegalStateException.class, () -> publisher.publish(
                UUID.randomUUID(), "document.failed", "electronic_document", UUID.randomUUID(), Map.of()));
    }
}
