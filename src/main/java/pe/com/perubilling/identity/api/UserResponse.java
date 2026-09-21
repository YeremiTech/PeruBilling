package pe.com.perubilling.identity.api;

import java.time.Instant;
import java.util.UUID;
import pe.com.perubilling.identity.domain.UserRole;

public record UserResponse(
        UUID id,
        String email,
        UserRole role,
        boolean enabled,
        Instant lastLoginAt,
        Instant lockedUntil,
        Instant createdAt) {}
