package pe.com.perubilling.summary.application;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Crea automáticamente Resúmenes Diarios por tenant/emisor/fecha.
 * La exclusión real entre nodos vive en DailySummaryService mediante advisory lock transaccional.
 */
@Component
public class DailySummaryScheduler {
    private static final Logger log = LoggerFactory.getLogger(DailySummaryScheduler.class);
    private static final ZoneId LIMA = ZoneId.of("America/Lima");

    private final JdbcClient jdbc;
    private final DailySummaryService service;
    private final boolean enabled;
    private final int minAgeMinutes;
    private final int maxGroups;
    private final int maxSummaryAgeDays;
    private final Counter created;
    private final Counter failed;
    private final Counter deadlineRisk;

    public DailySummaryScheduler(
            JdbcClient jdbc,
            DailySummaryService service,
            MeterRegistry registry,
            @Value("${app.summary.auto-enabled:true}") boolean enabled,
            @Value("${app.summary.auto-min-age-minutes:15}") int minAgeMinutes,
            @Value("${app.summary.auto-max-groups:100}") int maxGroups,
            @Value("${app.sunat.rules.max-summary-age-days:7}") int maxSummaryAgeDays) {
        this.jdbc = jdbc;
        this.service = service;
        this.enabled = enabled;
        this.minAgeMinutes = Math.max(1, minAgeMinutes);
        this.maxGroups = Math.max(1, Math.min(maxGroups, 500));
        this.maxSummaryAgeDays = Math.max(1, maxSummaryAgeDays);
        this.created = Counter.builder("perubilling.summary.auto.created").register(registry);
        this.failed = Counter.builder("perubilling.summary.auto.failed").register(registry);
        this.deadlineRisk = Counter.builder("perubilling.summary.auto.deadline_risk").register(registry);
    }

    @Scheduled(
            fixedDelayString = "${app.summary.auto-poll-ms:900000}",
            initialDelayString = "${app.summary.auto-initial-delay-ms:60000}")
    public void run() {
        if (!enabled) return;
        List<PendingGroup> groups = jdbc.sql("""
                select tenant_id, issuer_id, issue_date, count(*) as pending_count
                from electronic_document
                where status='PENDING_SUMMARY'
                  and created_at <= now() - make_interval(mins => :minAge)
                group by tenant_id, issuer_id, issue_date
                order by issue_date asc, min(created_at) asc
                limit :maxGroups
                """)
                .param("minAge", minAgeMinutes)
                .param("maxGroups", maxGroups)
                .query((rs, rowNum) -> new PendingGroup(
                        rs.getObject("tenant_id", UUID.class),
                        rs.getObject("issuer_id", UUID.class),
                        rs.getObject("issue_date", LocalDate.class),
                        rs.getLong("pending_count")))
                .list();

        LocalDate today = LocalDate.now(LIMA);
        for (PendingGroup group : groups) {
            try {
                long remainingDays = java.time.temporal.ChronoUnit.DAYS.between(today, group.issueDate().plusDays(maxSummaryAgeDays));
                if (remainingDays <= 1) deadlineRisk.increment();
                if (service.createAutomatically(group.tenantId(), group.issuerId(), group.issueDate()).isPresent()) {
                    created.increment();
                }
            } catch (Exception ex) {
                failed.increment();
                log.error("No se pudo crear Resumen Diario automático tenant={} issuer={} fecha={} pendientes={}",
                        group.tenantId(), group.issuerId(), group.issueDate(), group.pendingCount(), ex);
            }
        }
    }

    private record PendingGroup(UUID tenantId, UUID issuerId, LocalDate issueDate, long pendingCount) {}
}
