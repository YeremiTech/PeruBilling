package pe.com.perubilling.shared.security;

import java.util.Set;
import java.util.UUID;

public record ApiKeyPrincipal(UUID apiKeyId, UUID tenantId, String name, Set<String> scopes) {}
