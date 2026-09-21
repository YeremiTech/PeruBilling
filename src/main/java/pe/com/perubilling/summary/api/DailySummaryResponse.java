package pe.com.perubilling.summary.api;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import pe.com.perubilling.summary.domain.DailySummaryStatus;

public record DailySummaryResponse(UUID id, UUID issuerId, LocalDate referenceDate, String identifier,
                                   DailySummaryStatus status, String ticket, String responseCode,
                                   String responseMessage, Instant createdAt) {}
