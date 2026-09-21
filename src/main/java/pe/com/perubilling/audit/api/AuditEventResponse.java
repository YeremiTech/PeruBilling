package pe.com.perubilling.audit.api;

import java.time.Instant;
import java.util.UUID;

public record AuditEventResponse(
        UUID id,
        String actor,
        String method,
        String path,
        int status,
        String requestId,
        String remoteAddress,
        Instant createdAt) {}
