package pe.com.perubilling.voiding.application;

import java.time.Instant;
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
import pe.com.perubilling.cpe.validation.SunatXsdValidator;
import pe.com.perubilling.issuer.domain.SunatEnvironment;
import pe.com.perubilling.issuer.infrastructure.DigitalCertificateRepository;
import pe.com.perubilling.issuer.infrastructure.IssuerRepository;
import pe.com.perubilling.shared.storage.ArtifactStorage;
import pe.com.perubilling.summary.application.SunatSummaryGateway;
import pe.com.perubilling.sunat.domain.SunatClientException;
import pe.com.perubilling.voiding.domain.VoidingBatchStatus;
import pe.com.perubilling.voiding.infrastructure.VoidingBatchRepository;
import pe.com.perubilling.webhook.application.WebhookService;

@Service
public class VoidingProcessor {
    private final VoidingBatchRepository batches;
    private final ElectronicDocumentRepository documents;
    private final IssuerRepository issuers;
    private final DigitalCertificateRepository certificates;
    private final VoidingUblGenerator generator;
    private final XmlDocumentSigner signer;
    private final SunatXsdValidator xsdValidator;
    private final SunatSummaryGateway gateway;
    private final ArtifactStorage storage;
    private final DocumentStatusHistoryRepository histories;
    private final WebhookService webhooks;
    private final int maxAttempts;
    private final TransactionTemplate tx;

    public VoidingProcessor(VoidingBatchRepository batches, ElectronicDocumentRepository documents,
                            IssuerRepository issuers, DigitalCertificateRepository certificates,
                            VoidingUblGenerator generator, XmlDocumentSigner signer,
                            SunatXsdValidator xsdValidator, SunatSummaryGateway gateway,
                            ArtifactStorage storage, DocumentStatusHistoryRepository histories,
                            WebhookService webhooks, PlatformTransactionManager transactionManager,
                            @Value("${app.processing.max-attempts:6}") int maxAttempts) {
        this.batches=batches; this.documents=documents; this.issuers=issuers; this.certificates=certificates;
        this.generator=generator; this.signer=signer; this.xsdValidator=xsdValidator; this.gateway=gateway;
        this.storage=storage; this.histories=histories; this.webhooks=webhooks; this.maxAttempts=maxAttempts;
        this.tx=new TransactionTemplate(transactionManager);
    }

    public void process(UUID id) {
        var batch = batches.findById(id).orElse(null);
        if (batch == null) return;
        var document = documents.findById(batch.getDocumentId()).orElse(null);
        if (document == null) {
            batch.setStatus(VoidingBatchStatus.SEND_FAILED);
            batch.setResponseCode("DOCUMENT_NOT_FOUND");
            batch.setResponseMessage("Documento inexistente");
            batches.save(batch);
            return;
        }
        var issuer = issuers.findById(batch.getIssuerId()).orElseThrow();

        try {
            if (batch.getTicket() == null) {
                byte[] xml = generator.generate(batch, issuer, document);
                String base = issuer.getRuc() + "-" + batch.getIdentifier();
                batch.setXmlPath(storage.store(batch.getTenantId(), batch.getId(), base + ".xml", xml));

                byte[] signed = xml;
                if (issuer.getSunatEnvironment() != SunatEnvironment.LOCAL) {
                    Instant now = Instant.now();
                    var cert = certificates
                            .findFirstByTenantIdAndIssuerIdAndActiveTrueAndValidFromBeforeAndValidUntilAfterOrderByValidUntilDesc(
                                    batch.getTenantId(), issuer.getId(), now, now)
                            .orElseThrow(() -> new IllegalStateException("No existe certificado digital activo"));
                    signed = signer.sign(xml, cert);
                }
                xsdValidator.requireForEnvironment(issuer.getSunatEnvironment());
                xsdValidator.validateVoiding(signed);
                batch.setSignedXmlPath(storage.store(batch.getTenantId(), batch.getId(), base + "-signed.xml", signed));
                var ticket = gateway.submit(issuer, base, signed);
                batch.setTicket(ticket.ticket());
                batch.setStatus(VoidingBatchStatus.WAITING_TICKET);
                batch.setAttemptCount(batch.getAttemptCount()+1);
                batch.setNextRetryAt(Instant.now().plusSeconds(10));
                batches.save(batch);
                return;
            }

            var result = gateway.getStatus(issuer, batch.getTicket());
            if (result.processing()) {
                batch.setStatus(VoidingBatchStatus.WAITING_TICKET);
                batch.setNextRetryAt(Instant.now().plusSeconds(20));
                batches.save(batch);
                return;
            }

            batch.setResponseCode(result.statusCode());
            batch.setResponseMessage(result.description());
            String cdrPath = null;
            if (result.cdrZip()!=null) {
                cdrPath = storage.store(batch.getTenantId(), batch.getId(),
                        "R-" + issuer.getRuc() + "-" + batch.getIdentifier() + ".zip", result.cdrZip());
                batch.setCdrPath(cdrPath);
            }

            final String finalCdrPath = cdrPath;
            tx.executeWithoutResult(status -> {
    if (result.accepted()) {
        batch.setStatus(VoidingBatchStatus.ACCEPTED);
        document.setStatus(DocumentStatus.VOIDED);
        document.setCdrCode(result.statusCode());
        document.setCdrDescription(result.description());
        document.setCdrPath(finalCdrPath);
        documents.save(document);
        history(document.getTenantId(), document.getId(), DocumentStatus.VOIDED,
                result.statusCode(), "Baja aceptada mediante " + batch.getIdentifier());
        webhooks.publish(document.getTenantId(), "document.voided", document.getId(),
                java.util.Map.of("documentId",document.getId().toString(),"number",document.getFullNumber(),
                        "status","VOIDED","voiding",batch.getIdentifier()));
    } else {
        batch.setStatus(VoidingBatchStatus.REJECTED);
        restoreAccepted(document, result.statusCode(), result.description());
    }
    batch.setNextRetryAt(null);
    batches.save(batch);
            });
        } catch (SunatClientException ex) {
            if (ex.isRetryable() && batch.getTicket()==null && issuer.getSunatEnvironment()==SunatEnvironment.PRODUCTION) {
                markSubmissionUnknown(batch, ex.getCode(), ex.getMessage());
            } else {
                failOrRetry(batch, document, ex.getCode(), ex.getMessage(), ex.isRetryable());
            }
        } catch (Exception ex) {
            failOrRetry(batch, document, "VOIDING_ERROR",
                    ex.getMessage()==null?ex.getClass().getSimpleName():ex.getMessage(), false);
        }
    }

    private void markSubmissionUnknown(pe.com.perubilling.voiding.domain.VoidingBatchEntity batch, String code, String message) {
        tx.executeWithoutResult(status -> {
            batch.setStatus(VoidingBatchStatus.SUBMISSION_UNKNOWN);
            batch.setResponseCode(code);
            batch.setResponseMessage("Resultado de envío desconocido; requiere verificación operativa antes de reintentar. " + message);
            batch.setNextRetryAt(null);
            batches.save(batch);
        });
    }

    private void failOrRetry(pe.com.perubilling.voiding.domain.VoidingBatchEntity b,
                             pe.com.perubilling.billing.domain.ElectronicDocumentEntity d,
                             String code,String message,boolean retry) {
        tx.executeWithoutResult(status -> {
        int n=b.getAttemptCount()+1; b.setAttemptCount(n); b.setResponseCode(code); b.setResponseMessage(message);
        if(retry && n<maxAttempts){
            b.setStatus(VoidingBatchStatus.RETRY_PENDING);
            b.setNextRetryAt(Instant.now().plusSeconds(Math.min(900,10L*(1L<<Math.min(n,6)))));
        }else{
            b.setStatus(VoidingBatchStatus.SEND_FAILED); b.setNextRetryAt(null);
            restoreAccepted(d,code,message);
        }
        batches.save(b);
        });
    }

    private void restoreAccepted(pe.com.perubilling.billing.domain.ElectronicDocumentEntity d,String code,String message){
        d.setStatus(DocumentStatus.ACCEPTED);
        d.setLastErrorCode(code); d.setLastErrorMessage("Baja no aplicada: "+message);
        documents.save(d);
        history(d.getTenantId(),d.getId(),DocumentStatus.ACCEPTED,code,"La baja no fue aceptada; el comprobante conserva estado aceptado. "+message);
    }

    private void history(UUID tenantId,UUID docId,DocumentStatus status,String code,String msg){
        var h=new DocumentStatusHistoryEntity();h.setTenantId(tenantId);h.setDocumentId(docId);
        h.setStatus(status);h.setCode(code);h.setMessage(msg);histories.save(h);
    }
}
