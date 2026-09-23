package pe.com.perubilling.summary.application;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import pe.com.perubilling.summary.infrastructure.DailySummaryQueueRepository;

@Component
public class DailySummaryWorker {
    private static final Logger log = LoggerFactory.getLogger(DailySummaryWorker.class);

    private final DailySummaryQueueRepository queue;
    private final DailySummaryProcessor processor;
    private final int batch;
    private final int staleMinutes;

    public DailySummaryWorker(
            DailySummaryQueueRepository queue,
            DailySummaryProcessor processor,
            @Value("${app.processing.batch-size:10}") int batch,
            @Value("${app.processing.stale-minutes:15}") int staleMinutes) {
        this.queue = queue;
        this.processor = processor;
        this.batch = Math.max(1, Math.min(batch, 100));
        this.staleMinutes = Math.max(5, staleMinutes);
    }

    @Scheduled(fixedDelayString = "${app.processing.poll-ms:2000}")
    public void run() {
        queue.recoverStale(staleMinutes);
        for (int i = 0; i < batch; i++) {
            var claimed = queue.claim(1);
            if (claimed.isEmpty()) {
                return;
            }
            var id = claimed.getFirst();
            try {
                processor.process(id);
            } catch (Exception ex) {
                log.error("Fallo inesperado en Resumen Diario {}", id, ex);
            }
        }
    }
}
