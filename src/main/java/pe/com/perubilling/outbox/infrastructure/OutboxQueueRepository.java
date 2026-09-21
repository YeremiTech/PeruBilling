package pe.com.perubilling.outbox.infrastructure;

import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class OutboxQueueRepository {
    private final JdbcClient jdbc;
    public OutboxQueueRepository(JdbcClient jdbc){this.jdbc=jdbc;}

    @Transactional
    public List<UUID> claim(int limit) {
        return jdbc.sql("""
                WITH c AS (
                    SELECT id FROM outbox_event
                    WHERE status='PENDING' OR (status='RETRY' AND next_attempt_at<=now())
                    ORDER BY created_at
                    FOR UPDATE SKIP LOCKED
                    LIMIT :limit
                )
                UPDATE outbox_event o
                SET status='PROCESSING', processing_started_at=now(), updated_at=now()
                FROM c WHERE o.id=c.id
                RETURNING o.id
                """).param("limit",limit)
                .query((rs,rowNum)->rs.getObject("id", UUID.class)).list();
    }

    @Transactional
    public int recoverStale(int minutes) {
        return jdbc.sql("""
                UPDATE outbox_event
                SET status='RETRY', next_attempt_at=now(), processing_started_at=null,
                    last_error='Outbox recuperado tras expirar el lease', updated_at=now()
                WHERE status='PROCESSING'
                  AND processing_started_at < now() - (:minutes * interval '1 minute')
                """).param("minutes",minutes).update();
    }
}
