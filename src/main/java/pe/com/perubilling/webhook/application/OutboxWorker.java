package pe.com.perubilling.webhook.application;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import pe.com.perubilling.outbox.infrastructure.OutboxQueueRepository;

@Component
public class OutboxWorker {
    private static final Logger log=LoggerFactory.getLogger(OutboxWorker.class);
    private final OutboxQueueRepository queue;
    private final OutboxDispatcher dispatcher;
    private final int batchSize;

    public OutboxWorker(OutboxQueueRepository queue, OutboxDispatcher dispatcher,
                        @Value("${app.outbox.batch-size:50}") int batchSize) {
        this.queue=queue; this.dispatcher=dispatcher; this.batchSize=Math.max(1,Math.min(batchSize,200));
    }

    @Scheduled(fixedDelayString="${app.outbox.poll-ms:1000}")
    public void run() {
        queue.recoverStale(5);
        for(var id:queue.claim(batchSize)) {
            try { dispatcher.dispatch(id); }
            catch(Exception ex) { log.error("Fallo despachando outbox {}",id,ex); dispatcher.fail(id,ex.getMessage()); }
        }
    }
}
