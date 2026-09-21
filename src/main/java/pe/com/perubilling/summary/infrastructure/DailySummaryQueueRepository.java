package pe.com.perubilling.summary.infrastructure;

import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class DailySummaryQueueRepository {
    private final JdbcClient jdbc;
    public DailySummaryQueueRepository(JdbcClient jdbc){this.jdbc=jdbc;}

    @Transactional
    public List<UUID> claim(int limit) {
        return jdbc.sql("""
                WITH candidates AS (
                  SELECT id FROM daily_summary
                  WHERE status = 'QUEUED'
                     OR (status='WAITING_TICKET' AND next_retry_at <= now())
                     OR (status='RETRY_PENDING' AND next_retry_at <= now())
                  ORDER BY created_at
                  FOR UPDATE SKIP LOCKED LIMIT :limit
                )
                UPDATE daily_summary s SET status='PROCESSING', updated_at=now()
                FROM candidates c WHERE s.id=c.id RETURNING s.id
                """).param("limit",limit).query((rs,n)->rs.getObject("id",UUID.class)).list();
    }
    @Transactional public int recoverStale(int minutes){return jdbc.sql("""
            UPDATE daily_summary SET status=CASE WHEN ticket IS NULL THEN 'RETRY_PENDING' ELSE 'WAITING_TICKET' END,
            next_retry_at=now(),updated_at=now(),response_code='STALE_PROCESSING',
            response_message='Procesamiento recuperado tras quedar bloqueado'
            WHERE status='PROCESSING' AND updated_at < now() - (:m * interval '1 minute')
            """).param("m",minutes).update();}

}
