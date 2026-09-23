package pe.com.perubilling.shared.config;

import io.micrometer.core.instrument.MeterRegistry;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class OperationalMetrics {
    private final JdbcClient jdbc;
    private final AtomicInteger pendingSummary = new AtomicInteger();
    private final AtomicInteger submissionUnknown = new AtomicInteger();
    private final AtomicInteger rejected24h = new AtomicInteger();
    private final AtomicInteger certificatesExpiring30d = new AtomicInteger();

    public OperationalMetrics(JdbcClient jdbc, MeterRegistry registry) {
        this.jdbc = jdbc;
        registry.gauge("perubilling.documents.pending_summary", pendingSummary);
        registry.gauge("perubilling.documents.submission_unknown", submissionUnknown);
        registry.gauge("perubilling.documents.rejected_24h", rejected24h);
        registry.gauge("perubilling.certificates.expiring_30d", certificatesExpiring30d);
    }

    @Scheduled(fixedDelayString = "${app.operations.metrics-refresh-ms:60000}", initialDelayString = "${app.operations.metrics-initial-delay-ms:15000}")
    public void refresh() {
        pendingSummary.set(count("select count(*) from electronic_document where status='PENDING_SUMMARY'"));
        submissionUnknown.set(count("select count(*) from electronic_document where status in ('SUBMISSION_UNKNOWN','RECONCILIATION_REQUIRED')"));
        rejected24h.set(count("select count(*) from electronic_document where status='REJECTED' and updated_at >= now() - interval '24 hours'"));
        certificatesExpiring30d.set(count("select count(*) from digital_certificate where active=true and valid_until <= now() + interval '30 days'"));
    }

    private int count(String sql) {
        try { return Math.toIntExact(jdbc.sql(sql).query(Long.class).single()); }
        catch (Exception ignored) { return -1; }
    }
}
