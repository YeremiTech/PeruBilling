package pe.com.perubilling.webhook.application;

import java.time.Instant;
import java.util.Arrays;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.com.perubilling.outbox.infrastructure.OutboxEventRepository;
import pe.com.perubilling.webhook.domain.WebhookDeliveryEntity;
import pe.com.perubilling.webhook.infrastructure.WebhookDeliveryRepository;
import pe.com.perubilling.webhook.infrastructure.WebhookEndpointRepository;

@Service
public class OutboxDispatcher {
    private final OutboxEventRepository events;
    private final WebhookEndpointRepository endpoints;
    private final WebhookDeliveryRepository deliveries;

    public OutboxDispatcher(OutboxEventRepository events, WebhookEndpointRepository endpoints,
                            WebhookDeliveryRepository deliveries) {
        this.events=events; this.endpoints=endpoints; this.deliveries=deliveries;
    }

    @Transactional
    public void dispatch(UUID eventId) {
        var event=events.findById(eventId).orElse(null);
        if(event==null || "PUBLISHED".equals(event.getStatus())) return;
        for(var endpoint:endpoints.findAllByTenantIdAndActiveTrue(event.getTenantId())) {
            if(!Arrays.asList(endpoint.getEventTypes().split(",")).contains(event.getEventType())) continue;
            if(deliveries.existsBySourceEventIdAndEndpointId(event.getId(), endpoint.getId())) continue;
            var delivery=new WebhookDeliveryEntity();
            delivery.setTenantId(event.getTenantId());
            delivery.setEndpointId(endpoint.getId());
            delivery.setSourceEventId(event.getId());
            delivery.setEventId(UUID.randomUUID());
            delivery.setEventType(event.getEventType());
            delivery.setResourceId(event.getAggregateId());
            delivery.setPayload(event.getPayload());
            delivery.setStatus("PENDING");
            deliveries.save(delivery);
        }
        event.setStatus("PUBLISHED");
        event.setPublishedAt(Instant.now());
        event.setProcessingStartedAt(null);
        event.setLastError(null);
    }

    @Transactional
    public void fail(UUID eventId, String message) {
        var event=events.findById(eventId).orElse(null);
        if(event==null) return;
        int attempt=event.getAttemptCount()+1;
        event.setAttemptCount(attempt);
        event.setStatus(attempt>=10?"FAILED":"RETRY");
        event.setNextAttemptAt(attempt>=10?null:Instant.now().plusSeconds(Math.min(300, attempt*10L)));
        event.setProcessingStartedAt(null);
        event.setLastError(message==null?"Outbox dispatch failed":message.substring(0,Math.min(message.length(),1900)));
    }
}
