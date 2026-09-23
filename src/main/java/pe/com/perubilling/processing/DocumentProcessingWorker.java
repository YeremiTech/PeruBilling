package pe.com.perubilling.processing;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import pe.com.perubilling.billing.infrastructure.ProcessingQueueRepository;

@Component
public class DocumentProcessingWorker {
    private static final Logger log = LoggerFactory.getLogger(DocumentProcessingWorker.class);
    private final ProcessingQueueRepository queue;
    private final DocumentProcessor processor;
    private final int batchSize;
    private final int leaseMinutes;

    public DocumentProcessingWorker(
            ProcessingQueueRepository queue,
            DocumentProcessor processor,
            @Value("${app.processing.batch-size:10}") int batchSize,
            @Value("${app.processing.stale-minutes:15}") int leaseMinutes) {
        this.queue = queue;
        this.processor = processor;
        this.batchSize = Math.max(1, Math.min(batchSize, 100));
        this.leaseMinutes = Math.max(5, Math.min(leaseMinutes, 120));
    }

    @Scheduled(fixedDelayString = "${app.processing.poll-ms:2000}")
    public void run() {
        queue.recoverExpiredLeases();

        for (int i = 0; i < batchSize; i++) {
            var claimed = queue.claim(1, leaseMinutes);
            if (claimed.isEmpty()) break;
            var id = claimed.getFirst();
            try {
                processor.process(id);
            } catch (Exception ex) {
                log.error("Fallo inesperado procesando documento {}", id, ex);
            }
        }
    }
}
