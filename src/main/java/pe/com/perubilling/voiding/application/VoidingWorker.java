package pe.com.perubilling.voiding.application;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import pe.com.perubilling.voiding.infrastructure.VoidingQueueRepository;

@Component
public class VoidingWorker {
    private static final Logger log=LoggerFactory.getLogger(VoidingWorker.class);
    private final VoidingQueueRepository queue;
    private final VoidingProcessor processor;
    public VoidingWorker(VoidingQueueRepository queue,VoidingProcessor processor){this.queue=queue;this.processor=processor;}

    @Scheduled(fixedDelayString="${app.processing.poll-ms:2000}")
    public void run(){
        queue.recoverStale(15);
        for(var id:queue.claim(5)){
            try{processor.process(id);}catch(Exception ex){log.error("Fallo procesando Comunicación de Baja {}",id,ex);}
        }
    }
}
