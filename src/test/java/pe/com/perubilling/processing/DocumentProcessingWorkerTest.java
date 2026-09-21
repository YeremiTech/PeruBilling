package pe.com.perubilling.processing;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import pe.com.perubilling.billing.infrastructure.ProcessingQueueRepository;

class DocumentProcessingWorkerTest {
    @Test
    void recoversLeasesAndContinuesAfterUnexpectedProcessorFailure() {
        var queue = mock(ProcessingQueueRepository.class);
        var processor = mock(DocumentProcessor.class);
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        when(queue.claim(1, 15))
                .thenReturn(List.of(first))
                .thenReturn(List.of(second))
                .thenReturn(List.of());
        doThrow(new IllegalStateException("unexpected")).when(processor).process(first);
        var worker = new DocumentProcessingWorker(queue, processor, 10, 15);

        worker.run();

        verify(queue).recoverExpiredLeases();
        var order = inOrder(processor);
        order.verify(processor).process(first);
        order.verify(processor).process(second);
    }
}
