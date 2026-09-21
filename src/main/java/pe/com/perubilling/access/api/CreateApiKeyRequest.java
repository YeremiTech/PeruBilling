package pe.com.perubilling.access.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.Set;

public record CreateApiKeyRequest(
        @NotBlank @Size(max = 120) String name,
        @NotEmpty Set<String> scopes,
        Instant expiresAt
) {}
