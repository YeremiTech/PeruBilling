package pe.com.perubilling.identity.api;

import java.time.Instant;
import java.util.UUID;

public record LoginResponse(String accessToken, String tokenType, Instant expiresAt, UUID tenantId, String role) {}
