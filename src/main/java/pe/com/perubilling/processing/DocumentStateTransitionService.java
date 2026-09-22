package pe.com.perubilling.processing;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.com.perubilling.billing.domain.DocumentStatus;
import pe.com.perubilling.billing.domain.DocumentStatusHistoryEntity;
import pe.com.perubilling.billing.domain.SubmissionAttemptEntity;
import pe.com.perubilling.billing.infrastructure.DocumentStatusHistoryRepository;
import pe.com.perubilling.billing.infrastructure.ElectronicDocumentRepository;
import pe.com.perubilling.billing.infrastructure.SubmissionAttemptRepository;
import pe.com.perubilling.outbox.application.OutboxPublisher;
import pe.com.perubilling.sunat.domain.SunatSubmissionResult;

@Service
public class DocumentStateTransitionService {
    private final ElectronicDocumentRepository documents;
    private final DocumentStatusHistoryRepository histories;
    private final SubmissionAttemptRepository attempts;
    private final OutboxPublisher outbox;
    private final int maxAttempts;
    private final int maxReconciliations;

    public DocumentStateTransitionService(ElectronicDocumentRepository documents,
                                          DocumentStatusHistoryRepository histories,
                                          SubmissionAttemptRepository attempts,
                                          OutboxPublisher outbox,
                                          @Value("${app.processing.max-attempts:6}") int maxAttempts,
                                          @Value("${app.processing.max-reconciliations:8}") int maxReconciliations) {
        this.documents=documents; this.histories=histories; this.attempts=attempts; this.outbox=outbox;
        this.maxAttempts=Math.max(1,maxAttempts); this.maxReconciliations=Math.max(1,maxReconciliations);
    }

    @Transactional
    public AttemptContext startAttempt(UUID documentId, Instant started) {
        var document=documents.findById(documentId).orElse(null);
        if(document==null) return null;
        var attempt=new SubmissionAttemptEntity();
        attempt.setTenantId(document.getTenantId());
        attempt.setDocumentId(documentId);
        attempt.setAttemptNumber(document.getAttemptCount()+1);
        attempt.setStartedAt(started);
        attempt.setSuccess(false);
        attempts.save(attempt);
        return new AttemptContext(attempt.getId(),attempt.getAttemptNumber(),started);
    }

    @Transactional
    public void saveArtifacts(UUID documentId, String xmlPath, String signedXmlPath, String pdfPath, String thermalPdfPath, String xmlHash) {
        var document=require(documentId);
        document.setXmlPath(xmlPath);
        document.setSignedXmlPath(signedXmlPath);
        document.setPdfPath(pdfPath);
        document.setThermalPdfPath(thermalPdfPath);
        document.setXmlHash(xmlHash);
    }

    @Transactional
    public void markSummaryReady(UUID documentId, AttemptContext attempt) {
        var document=require(documentId);
        document.setStatus(DocumentStatus.PENDING_SUMMARY);
        document.setAttemptCount(attempt.number());
        document.setNextRetryAt(null);
        document.setLastErrorCode(null);
        document.setLastErrorMessage(null);
        clearLease(document);
        history(document,DocumentStatus.PENDING_SUMMARY,"SUMMARY_READY","XML/PDF preparados; documento listo para Resumen Diario");
        finishAttempt(attempt,true,null,"SUMMARY_READY","Artefactos preparados");
    }

    @Transactional
    public void markSubmitting(UUID documentId) {
        var document=require(documentId);
        document.setStatus(DocumentStatus.SUBMITTING);
        history(document,DocumentStatus.SUBMITTING,"SUBMITTING","Envío a SUNAT iniciado");
    }

    @Transactional
    public void commitSunatResult(UUID documentId, AttemptContext attempt, SunatSubmissionResult result,
                                  String cdrPath, boolean recovered) {
        var document=require(documentId);
        document.setCdrCode(result.responseCode());
        document.setCdrDescription(result.description());
        if(cdrPath!=null) document.setCdrPath(cdrPath);
        document.setAttemptCount(attempt.number());
        document.setNextRetryAt(null);
        document.setLastErrorCode(null);
        document.setLastErrorMessage(null);
        document.setSubmissionUnknownSince(null);
        document.setReconciliationCount(0);
        clearLease(document);

        String code=recovered?"RECOVERED_"+result.responseCode():result.responseCode();
        String event;
        if(result.accepted()) {
            DocumentStatus finalStatus=result.observed()?DocumentStatus.OBSERVED:DocumentStatus.ACCEPTED;
            document.setStatus(finalStatus);
            document.setAcceptedAt(Instant.now());
            history(document,finalStatus,code,buildMessage(result));
            event=finalStatus==DocumentStatus.OBSERVED?"document.observed":"document.accepted";
        } else if("SUNAT_STATUS_0003".equals(result.responseCode())) {
            document.setStatus(DocumentStatus.VOIDED);
            history(document,DocumentStatus.VOIDED,code,result.description());
            event="document.voided";
        } else {
            document.setStatus(DocumentStatus.REJECTED);
            history(document,DocumentStatus.REJECTED,code,result.description());
            event="document.rejected";
        }
        outbox.publish(document.getTenantId(),event,"electronic_document",documentId,webhookData(document,result.description()));
        finishAttempt(attempt,true,result.httpStatus(),code,result.description());
    }

    @Transactional
    public void markSubmissionUnknown(UUID documentId, AttemptContext attempt, String code, String message) {
        var document=require(documentId);
        document.setAttemptCount(attempt.number());
        document.setStatus(DocumentStatus.SUBMISSION_UNKNOWN);
        document.setSubmissionUnknownSince(document.getSubmissionUnknownSince()==null?Instant.now():document.getSubmissionUnknownSince());
        document.setReconciliationCount(0);
        document.setNextRetryAt(Instant.now().plusSeconds(30));
        document.setLastErrorCode(code);
        document.setLastErrorMessage(message);
        clearLease(document);
        history(document,DocumentStatus.SUBMISSION_UNKNOWN,code,
                "Resultado del envío incierto; se consultará SUNAT antes de cualquier reenvío. "+safe(message));
        finishAttempt(attempt,false,null,code,message);
    }

    @Transactional
    public void confirmedNotFoundForRetry(UUID documentId, AttemptContext attempt, String message) {
        var document=require(documentId);
        document.setAttemptCount(attempt.number());
        document.setStatus(DocumentStatus.RETRY_PENDING);
        document.setSubmissionUnknownSince(null);
        document.setReconciliationCount(0);
        document.setNextRetryAt(Instant.now().plusSeconds(15));
        document.setLastErrorCode("SUNAT_CONFIRMED_NOT_FOUND");
        document.setLastErrorMessage(safe(message));
        clearLease(document);
        history(document,DocumentStatus.RETRY_PENDING,"SUNAT_CONFIRMED_NOT_FOUND",
                "SUNAT confirmó que el comprobante no existe; se habilita un reenvío controlado");
        finishAttempt(attempt,false,null,"SUNAT_CONFIRMED_NOT_FOUND",message);
    }

    @Transactional
    public void reconciliationNotFound(UUID documentId, AttemptContext attempt, String message) {
        var document=require(documentId);
        int count=document.getReconciliationCount()+1;
        document.setAttemptCount(attempt.number());
        document.setReconciliationCount(count);
        document.setLastErrorCode("SUNAT_RECONCILIATION_PENDING");
        document.setLastErrorMessage(message);
        clearLease(document);
        if(count>=maxReconciliations) {
            document.setStatus(DocumentStatus.RECONCILIATION_REQUIRED);
            document.setNextRetryAt(null);
            history(document,DocumentStatus.RECONCILIATION_REQUIRED,"SUNAT_RECONCILIATION_REQUIRED",
                    "No fue posible confirmar automáticamente el estado en SUNAT; requiere revisión manual antes de reenviar");
            outbox.publish(document.getTenantId(),"document.failed","electronic_document",documentId,
                    webhookData(document,"Estado SUNAT incierto; requiere reconciliación manual"));
        } else {
            document.setStatus(DocumentStatus.SUBMISSION_UNKNOWN);
            document.setNextRetryAt(Instant.now().plus(reconciliationBackoff(count)));
            history(document,DocumentStatus.SUBMISSION_UNKNOWN,"SUNAT_RECONCILIATION_PENDING",
                    "SUNAT aún no devuelve CDR/estado. Reconciliación "+count+"/"+maxReconciliations);
        }
        finishAttempt(attempt,false,null,"SUNAT_RECONCILIATION_PENDING",message);
    }

    @Transactional
    public void retryOrFail(UUID documentId, AttemptContext attempt, String code, String message, boolean retryable) {
        var document=documents.findById(documentId).orElse(null);
        if(document==null) return;
        int number=attempt.number();
        document.setAttemptCount(number);
        document.setLastErrorCode(code);
        document.setLastErrorMessage(message);
        clearLease(document);
        if(retryable && number<maxAttempts) {
            document.setStatus(DocumentStatus.RETRY_PENDING);
            document.setNextRetryAt(Instant.now().plus(backoff(number)));
            history(document,DocumentStatus.RETRY_PENDING,code,message);
        } else {
            document.setStatus(DocumentStatus.SEND_FAILED);
            document.setNextRetryAt(null);
            history(document,DocumentStatus.SEND_FAILED,code,message);
            outbox.publish(document.getTenantId(),"document.failed","electronic_document",documentId,webhookData(document,message));
        }
        finishAttempt(attempt,false,null,code,message);
    }

    private void finishAttempt(AttemptContext context, boolean success, Integer httpStatus, String code, String message) {
        var attempt=attempts.findById(context.id()).orElse(null);
        if(attempt==null) return;
        Instant finished=Instant.now();
        attempt.setSuccess(success);
        attempt.setHttpStatus(httpStatus);
        attempt.setResponseCode(code);
        attempt.setResponseMessage(safe(message));
        attempt.setFinishedAt(finished);
        attempt.setDurationMs(Duration.between(context.started(),finished).toMillis());
    }

    private void history(pe.com.perubilling.billing.domain.ElectronicDocumentEntity d, DocumentStatus status,String code,String message) {
        var h=new DocumentStatusHistoryEntity();
        h.setTenantId(d.getTenantId()); h.setDocumentId(d.getId()); h.setStatus(status); h.setCode(code); h.setMessage(safe(message));
        histories.save(h);
    }

    private void clearLease(pe.com.perubilling.billing.domain.ElectronicDocumentEntity d) {
        d.setProcessingLeaseUntil(null);
        d.setProcessingStartedAt(null);
    }

    private pe.com.perubilling.billing.domain.ElectronicDocumentEntity require(UUID id) {
        return documents.findById(id).orElseThrow(()->new IllegalStateException("Documento inexistente "+id));
    }

    private Duration backoff(int attempt) {
        long seconds=Math.min(900,(long)Math.pow(3,Math.max(0,attempt-1))*10L);
        long jitter=Math.abs(UUID.randomUUID().getLeastSignificantBits()%8L);
        return Duration.ofSeconds(seconds+jitter);
    }

    private Duration reconciliationBackoff(int attempt) {
        return Duration.ofSeconds(Math.min(300,30L*(1L<<Math.min(3,Math.max(0,attempt-1)))));
    }

    private Map<String,Object> webhookData(pe.com.perubilling.billing.domain.ElectronicDocumentEntity d,String message) {
        return Map.of("documentId",d.getId().toString(),"number",d.getFullNumber(),"status",d.getStatus().name(),"message",message==null?"":message);
    }

    private String buildMessage(SunatSubmissionResult result) {
        if(result.notes()==null || result.notes().isEmpty()) return result.description();
        return result.description()+" | Observaciones: "+String.join(" | ",result.notes());
    }

    private String safe(String message) {
        if(message==null) return "";
        return message.substring(0,Math.min(message.length(),1900));
    }

    public record AttemptContext(UUID id,int number,Instant started) {}
}
