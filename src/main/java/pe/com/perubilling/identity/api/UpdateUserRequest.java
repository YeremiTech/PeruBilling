package pe.com.perubilling.identity.api;

import pe.com.perubilling.identity.domain.UserRole;

public record UpdateUserRequest(Boolean enabled, UserRole role) {}
