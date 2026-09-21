package pe.com.perubilling.access.api;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

public record ApiKeyCreatedResponse(
        UUID id, String name, String apiKey, String prefix, Set<String> scopes, Instant expiresAt
) {}
