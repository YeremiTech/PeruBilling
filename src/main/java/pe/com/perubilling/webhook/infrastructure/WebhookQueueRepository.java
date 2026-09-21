package pe.com.perubilling.webhook.infrastructure;

import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class WebhookQueueRepository {
    private final JdbcClient jdbc;
    public WebhookQueueRepository(JdbcClient jdbc) { this.jdbc = jdbc; }

    @Transactional
    public List<UUID> claim(int limit) {
        return jdbc.sql("""
                WITH c AS (
                    SELECT id FROM webhook_delivery
                    WHERE status='PENDING' OR (status='RETRY' AND next_attempt_at<=now())
                    ORDER BY created_at
                    FOR UPDATE SKIP LOCKED
                    LIMIT :limit
                )
                UPDATE webhook_delivery w
                SET status='SENDING', sending_started_at=now(), updated_at=now()
                FROM c WHERE w.id=c.id
                RETURNING w.id
                """)
                .param("limit", limit)
                .query((rs, rowNum) -> rs.getObject("id", UUID.class))
                .list();
    }

    @Transactional
    public int recoverStaleSending(int minutes) {
        return jdbc.sql("""
                UPDATE webhook_delivery
                SET status='RETRY',
                    next_attempt_at=now(),
                    sending_started_at=null,
                    last_error='Entrega recuperada después de expirar el lease SENDING',
                    updated_at=now()
                WHERE status='SENDING'
                  AND sending_started_at < now() - (:minutes * interval '1 minute')
                """)
                .param("minutes", minutes)
                .update();
    }
}
