package pe.com.perubilling.voiding.infrastructure;

import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class VoidingQueueRepository {
    private final JdbcClient jdbc;
    public VoidingQueueRepository(JdbcClient jdbc){this.jdbc=jdbc;}

    @Transactional
    public List<UUID> claim(int limit){
        return jdbc.sql("""
            WITH c AS (
              SELECT id FROM voiding_batch
              WHERE status='QUEUED'
                 OR (status='WAITING_TICKET' AND next_retry_at<=now())
                 OR (status='RETRY_PENDING' AND next_retry_at<=now())
              ORDER BY created_at
              FOR UPDATE SKIP LOCKED
              LIMIT :limit
            )
            UPDATE voiding_batch v
            SET status='PROCESSING', updated_at=now()
            FROM c WHERE v.id=c.id
            RETURNING v.id
            """).param("limit",limit).query((rs,n)->rs.getObject("id",UUID.class)).list();
    }

    @Transactional
    public int recoverStale(int minutes){
        return jdbc.sql("""
            UPDATE voiding_batch
            SET status=CASE WHEN ticket IS NULL THEN 'RETRY_PENDING' ELSE 'WAITING_TICKET' END,
                next_retry_at=now(),
                response_code='STALE_PROCESSING',
                response_message='Comunicación de baja recuperada tras quedar bloqueada',
                updated_at=now()
            WHERE status='PROCESSING' AND updated_at < now() - (:minutes * interval '1 minute')
            """).param("minutes",minutes).update();
    }
}
