package pe.com.perubilling.billing.infrastructure;

import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class ProcessingQueueRepository {
    private final JdbcClient jdbc;
    public ProcessingQueueRepository(JdbcClient jdbc) { this.jdbc = jdbc; }

    @Transactional
    public List<UUID> claim(int limit, int leaseMinutes) {
        return jdbc.sql("""
                WITH candidates AS (
                    SELECT id, status AS previous_status
                    FROM electronic_document
                    WHERE (
                        status IN ('QUEUED','SUMMARY_PREPARATION_QUEUED')
                        OR (status = 'RETRY_PENDING' AND next_retry_at <= now())
                        OR (status = 'SUBMISSION_UNKNOWN' AND next_retry_at <= now())
                    )
                    AND (processing_lease_until IS NULL OR processing_lease_until < now())
                    ORDER BY created_at
                    FOR UPDATE SKIP LOCKED
                    LIMIT :limit
                )
                UPDATE electronic_document d
                SET status = CASE WHEN c.previous_status='SUBMISSION_UNKNOWN' THEN 'SUBMISSION_UNKNOWN' ELSE 'PROCESSING' END,
                    processing_started_at = now(),
                    processing_lease_until = now() + (:leaseMinutes * interval '1 minute'),
                    updated_at = now()
                FROM candidates c
                WHERE d.id = c.id
                RETURNING d.id
                """)
                .param("limit", limit)
                .param("leaseMinutes", leaseMinutes)
                .query((rs, rowNum) -> rs.getObject("id", UUID.class))
                .list();
    }

    @Transactional
    public int recoverExpiredLeases() {
        int processing = jdbc.sql("""
                UPDATE electronic_document
                SET status='RETRY_PENDING', next_retry_at=now(), processing_started_at=null,
                    processing_lease_until=null, updated_at=now(),
                    last_error_code='PROCESSING_LEASE_EXPIRED',
                    last_error_message='Procesamiento recuperado tras expirar el lease antes de iniciar el envío SUNAT'
                WHERE status='PROCESSING' AND processing_lease_until IS NOT NULL AND processing_lease_until < now()
                """).update();
        int submitting = jdbc.sql("""
                UPDATE electronic_document
                SET status='SUBMISSION_UNKNOWN', next_retry_at=now(), processing_started_at=null,
                    processing_lease_until=null, submission_unknown_since=COALESCE(submission_unknown_since,now()),
                    updated_at=now(), last_error_code='SUBMITTING_LEASE_EXPIRED',
                    last_error_message='El worker cayó durante el envío; se reconciliará con SUNAT antes de reenviar'
                WHERE status='SUBMITTING' AND processing_lease_until IS NOT NULL AND processing_lease_until < now()
                """).update();
        int unknown = jdbc.sql("""
                UPDATE electronic_document
                SET next_retry_at=now(), processing_started_at=null, processing_lease_until=null, updated_at=now()
                WHERE status='SUBMISSION_UNKNOWN' AND processing_lease_until IS NOT NULL AND processing_lease_until < now()
                """).update();
        return processing + submitting + unknown;
    }
}
