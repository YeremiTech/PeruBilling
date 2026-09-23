package pe.com.perubilling.summary.application;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import pe.com.perubilling.billing.domain.DocumentStatus;
import pe.com.perubilling.billing.domain.DocumentStatusHistoryEntity;
import pe.com.perubilling.billing.infrastructure.DocumentStatusHistoryRepository;
import pe.com.perubilling.billing.infrastructure.ElectronicDocumentRepository;
import pe.com.perubilling.cpe.signature.XmlDocumentSigner;
import pe.com.perubilling.cpe.signature.XmlSignatureVerifier;
import pe.com.perubilling.cpe.validation.SunatXsdValidator;
import pe.com.perubilling.issuer.domain.SunatEnvironment;
import pe.com.perubilling.issuer.infrastructure.DigitalCertificateRepository;
import pe.com.perubilling.issuer.infrastructure.IssuerRepository;
import pe.com.perubilling.shared.storage.ArtifactStorage;
import pe.com.perubilling.summary.domain.DailySummaryStatus;
import pe.com.perubilling.summary.infrastructure.DailySummaryRepository;
import pe.com.perubilling.sunat.domain.SunatClientException;
import pe.com.perubilling.webhook.application.WebhookService;

@Service
public class DailySummaryProcessor {
    private static final ZoneId LIMA=ZoneId.of("America/Lima");
    private final DailySummaryRepository summaries;
    private final ElectronicDocumentRepository documents;
    private final IssuerRepository issuers;
    private final DigitalCertificateRepository certificates;
    private final DailySummaryUblGenerator generator;
    private final XmlDocumentSigner signer;
    private final XmlSignatureVerifier signatureVerifier;
    private final SunatXsdValidator xsdValidator;
    private final SunatSummaryGateway gateway;
    private final ArtifactStorage storage;
    private final DocumentStatusHistoryRepository histories;
    private final WebhookService webhooks;
    private final int maxAttempts;
    private final TransactionTemplate tx;

    public DailySummaryProcessor(
            DailySummaryRepository summaries,
            ElectronicDocumentRepository documents,
            IssuerRepository issuers,
            DigitalCertificateRepository certificates,
            DailySummaryUblGenerator generator,
            XmlDocumentSigner signer,
            XmlSignatureVerifier signatureVerifier,
            SunatXsdValidator xsdValidator,
            SunatSummaryGateway gateway,
            ArtifactStorage storage,
            DocumentStatusHistoryRepository histories,
            WebhookService webhooks,
            PlatformTransactionManager transactionManager,
            @Value("${app.processing.max-attempts:6}") int maxAttempts) {
        this.summaries=summaries;this.documents=documents;this.issuers=issuers;this.certificates=certificates;
        this.generator=generator;this.signer=signer;this.signatureVerifier=signatureVerifier;this.xsdValidator=xsdValidator;this.gateway=gateway;
        this.storage=storage;this.histories=histories;this.webhooks=webhooks;this.maxAttempts=maxAttempts;
        this.tx=new TransactionTemplate(transactionManager);
    }

    public void process(UUID id){
        var summary=summaries.findById(id).orElse(null);
        if(summary==null)return;
        var issuer=issuers.findById(summary.getIssuerId()).orElseThrow();
        var docs=documents.findAllByDailySummaryIdOrderByCreatedAtAsc(summary.getId());

        try{
            if(summary.getTicket()==null){
                byte[] xml=generator.generate(summary,issuer,docs,LocalDate.now(LIMA));
                String base=issuer.getRuc()+"-"+summary.getIdentifier();
                summary.setXmlPath(storage.store(summary.getTenantId(),summary.getId(),base+".xml",xml));

                byte[] signed=xml;
                if(issuer.getSunatEnvironment()!=SunatEnvironment.LOCAL){
                    Instant now=Instant.now();
                    var cert=certificates
                            .findFirstByTenantIdAndIssuerIdAndActiveTrueAndValidFromBeforeAndValidUntilAfterOrderByValidUntilDesc(
                                    summary.getTenantId(),issuer.getId(),now,now)
                            .orElseThrow(()->new IllegalStateException("No existe certificado digital activo"));
                    signed=signer.sign(xml,cert);
                    signatureVerifier.requireValid(signed);
                }
                xsdValidator.requireForEnvironment(issuer.getSunatEnvironment());
                xsdValidator.validateSummary(signed);
                summary.setSignedXmlPath(storage.store(summary.getTenantId(),summary.getId(),base+"-signed.xml",signed));
                var ticket=gateway.submit(issuer,base,signed);
                summary.setTicket(ticket.ticket());
                summary.setStatus(DailySummaryStatus.WAITING_TICKET);
                summary.setAttemptCount(summary.getAttemptCount()+1);
                summary.setNextRetryAt(Instant.now().plusSeconds(10));
                summaries.save(summary);
                return;
            }

            var result=gateway.getStatus(issuer,summary.getTicket());
            if(result.processing()){
                summary.setStatus(DailySummaryStatus.WAITING_TICKET);
                summary.setNextRetryAt(Instant.now().plusSeconds(20));
                summaries.save(summary);
                return;
            }

            summary.setResponseCode(result.statusCode());
            summary.setResponseMessage(result.description());
            String summaryCdrPath=null;
            if(result.cdrZip()!=null){
                summaryCdrPath=storage.store(summary.getTenantId(),summary.getId(),
                        "R-"+issuer.getRuc()+"-"+summary.getIdentifier()+".zip",result.cdrZip());
                summary.setCdrPath(summaryCdrPath);
            }

            final String finalSummaryCdrPath = summaryCdrPath;
            tx.executeWithoutResult(status -> {
    if(result.accepted()){
        summary.setStatus(DailySummaryStatus.ACCEPTED);
        for(var d:docs){
            boolean voiding="3".equals(d.getSummaryConditionCode());
            DocumentStatus finalStatus=voiding?DocumentStatus.VOIDED:DocumentStatus.ACCEPTED;
            d.setStatus(finalStatus);
            if(!voiding)d.setAcceptedAt(Instant.now());
            d.setCdrCode(result.statusCode());
            d.setCdrDescription(result.description());
            d.setCdrPath(finalSummaryCdrPath);
            documents.save(d);
            history(d.getTenantId(),d.getId(),finalStatus,result.statusCode(),
                    (voiding?"Baja aceptada":"Aceptado")+" mediante "+summary.getIdentifier());
            webhooks.publish(d.getTenantId(),voiding?"document.voided":"document.accepted",d.getId(),
                    java.util.Map.of("documentId",d.getId().toString(),"number",d.getFullNumber(),
                            "status",finalStatus.name(),"summary",summary.getIdentifier()));
        }
    }else{
        summary.setStatus(DailySummaryStatus.REJECTED);
        for(var d:docs){
            if("3".equals(d.getSummaryConditionCode())){
                restoreAcceptedAfterVoidFailure(d,result.statusCode(),result.description());
            }else{
                d.setStatus(DocumentStatus.REJECTED);
                d.setCdrCode(result.statusCode());
                d.setCdrDescription(result.description());
                d.setCdrPath(finalSummaryCdrPath);
                documents.save(d);
                history(d.getTenantId(),d.getId(),DocumentStatus.REJECTED,result.statusCode(),result.description());
                webhooks.publish(d.getTenantId(),"document.rejected",d.getId(),
                        java.util.Map.of("documentId",d.getId().toString(),"number",d.getFullNumber(),
                                "status","REJECTED","summary",summary.getIdentifier()));
            }
        }
    }
    summary.setNextRetryAt(null);
    summaries.save(summary);
            });
        }catch(SunatClientException ex){
            if (ex.isRetryable() && summary.getTicket()==null && issuer.getSunatEnvironment()==SunatEnvironment.PRODUCTION) {
                markSubmissionUnknown(summary, ex.getCode(), ex.getMessage());
            } else {
                failOrRetry(summary,ex.getCode(),ex.getMessage(),ex.isRetryable());
            }
        }catch(Exception ex){
            failOrRetry(summary,"SUMMARY_ERROR",
                    ex.getMessage()==null?ex.getClass().getSimpleName():ex.getMessage(),false);
        }
    }

    private void markSubmissionUnknown(pe.com.perubilling.summary.domain.DailySummaryEntity summary, String code, String message) {
        tx.executeWithoutResult(status -> {
            summary.setStatus(DailySummaryStatus.SUBMISSION_UNKNOWN);
            summary.setResponseCode(code);
            summary.setResponseMessage("Resultado de envío desconocido; requiere verificación operativa antes de reintentar. " + message);
            summary.setNextRetryAt(null);
            summaries.save(summary);
        });
    }

    private void failOrRetry(pe.com.perubilling.summary.domain.DailySummaryEntity summary,
                             String code,String message,boolean retry){
        tx.executeWithoutResult(status -> {
        int n=summary.getAttemptCount()+1;
        summary.setAttemptCount(n);
        summary.setResponseCode(code);
        summary.setResponseMessage(message);
        if(retry&&n<maxAttempts){
            summary.setStatus(DailySummaryStatus.RETRY_PENDING);
            summary.setNextRetryAt(Instant.now().plusSeconds(Math.min(900,10L*(1L<<Math.min(n,6)))));
        }else{
            summary.setStatus(DailySummaryStatus.SEND_FAILED);
            summary.setNextRetryAt(null);
            for(var d:documents.findAllByDailySummaryIdOrderByCreatedAtAsc(summary.getId())){
                if("3".equals(d.getSummaryConditionCode())){
                    restoreAcceptedAfterVoidFailure(d,code,message);
                }else{
                    d.setStatus(DocumentStatus.SEND_FAILED);
                    d.setLastErrorCode(code);
                    d.setLastErrorMessage(message);
                    documents.save(d);
                    history(d.getTenantId(),d.getId(),DocumentStatus.SEND_FAILED,code,message);
                    webhooks.publish(d.getTenantId(),"document.failed",d.getId(),
                            java.util.Map.of("documentId",d.getId().toString(),"number",d.getFullNumber(),
                                    "status","SEND_FAILED","summary",summary.getIdentifier()));
                }
            }
        }
        summaries.save(summary);
        });
    }

    private void restoreAcceptedAfterVoidFailure(
            pe.com.perubilling.billing.domain.ElectronicDocumentEntity d,String code,String message){
        DocumentStatus restoredStatus = previousStableStatus(d);
        d.setStatus(restoredStatus);
        d.setLastErrorCode(code);
        d.setLastErrorMessage("Baja no aplicada: "+message);
        d.setDailySummaryId(null);
        d.setSummaryConditionCode("1");
        documents.save(d);
        history(d.getTenantId(),d.getId(),restoredStatus,code,
                "La baja no fue aceptada; el comprobante conserva estado "+restoredStatus.name()+". "+message);
    }

    private DocumentStatus previousStableStatus(pe.com.perubilling.billing.domain.ElectronicDocumentEntity document) {
        return histories.findFirstByTenantIdAndDocumentIdAndStatusInOrderByCreatedAtDesc(
                        document.getTenantId(), document.getId(),
                        java.util.List.of(DocumentStatus.ACCEPTED, DocumentStatus.OBSERVED))
                .map(DocumentStatusHistoryEntity::getStatus)
                .orElse(DocumentStatus.ACCEPTED);
    }

    private void history(UUID tenantId,UUID docId,DocumentStatus status,String code,String msg){
        var h=new DocumentStatusHistoryEntity();
        h.setTenantId(tenantId);h.setDocumentId(docId);h.setStatus(status);h.setCode(code);h.setMessage(msg);
        histories.save(h);
    }
}
