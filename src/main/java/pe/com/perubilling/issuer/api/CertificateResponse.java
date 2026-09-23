package pe.com.perubilling.issuer.api;

import java.time.Instant;
import java.util.UUID;

public record CertificateResponse(UUID id, String alias, String fingerprint, String subjectDn, String serialNumber,
                                  Instant validFrom, Instant validUntil, boolean active) {}
