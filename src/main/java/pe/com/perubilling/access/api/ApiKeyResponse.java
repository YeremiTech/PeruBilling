package pe.com.perubilling.access.api;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

public record ApiKeyResponse(
        UUID id, String name, String prefix, Set<String> scopes,
        Instant lastUsedAt, Instant expiresAt, Instant revokedAt, Instant createdAt
) {}
