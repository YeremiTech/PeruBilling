package pe.com.perubilling.webhook.application;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import pe.com.perubilling.outbox.infrastructure.OutboxQueueRepository;

class OutboxWorkerTest {
    @Test
    void recoversStaleEventsAndMarksDispatchFailureForRetry() {
        var queue = mock(OutboxQueueRepository.class);
        var dispatcher = mock(OutboxDispatcher.class);
        UUID id = UUID.randomUUID();
        when(queue.claim(50)).thenReturn(List.of(id));
        doThrow(new IllegalStateException("dispatch failed")).when(dispatcher).dispatch(id);
        var worker = new OutboxWorker(queue, dispatcher, 50);

        worker.run();

        verify(queue).recoverStale(5);
        verify(dispatcher).fail(id, "dispatch failed");
    }
}
