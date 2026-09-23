package pe.com.perubilling.webhook.api;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

public record WebhookResponse(
        UUID id,
        String url,
        Set<String> eventTypes,
        boolean active,
        Instant createdAt,
        String signingSecret) {}
