package pe.com.perubilling.delivery.api;

import java.time.Instant;

public record DocumentAccessLinkResponse(String token, String url, Instant expiresAt) {}
