package pe.com.perubilling.outbox.application;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;
import pe.com.perubilling.outbox.domain.OutboxEventEntity;
import pe.com.perubilling.outbox.infrastructure.OutboxEventRepository;
import tools.jackson.databind.json.JsonMapper;

@Component
public class OutboxPublisher {
    private final OutboxEventRepository events;
    private final JsonMapper mapper;

    public OutboxPublisher(OutboxEventRepository events, JsonMapper mapper) {
        this.events = events;
        this.mapper = mapper;
    }

    public UUID publish(UUID tenantId, String eventType, String aggregateType, UUID aggregateId, Map<String,Object> data) {
        try {
            UUID eventId = UUID.randomUUID();
            String payload = mapper.writeValueAsString(Map.of(
                    "eventId", eventId.toString(),
                    "type", eventType,
                    "createdAt", Instant.now().toString(),
                    "data", data));
            OutboxEventEntity e = new OutboxEventEntity();
            // Keep the JPA-generated storage id unset. Spring Data decides whether to
            // persist or merge from the entity id; pre-assigning the generated id makes
            // a brand-new outbox row look detached under Hibernate 7. The envelope event
            // id is intentionally independent from the outbox row primary key.
            e.setTenantId(tenantId);
            e.setEventType(eventType);
            e.setAggregateType(aggregateType);
            e.setAggregateId(aggregateId);
            e.setPayload(payload);
            events.save(e);
            return eventId;
        } catch (Exception ex) {
            throw new IllegalStateException("No se pudo registrar el evento transaccional", ex);
        }
    }
}
