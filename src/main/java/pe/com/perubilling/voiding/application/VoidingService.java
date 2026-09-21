package pe.com.perubilling.voiding.application;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.com.perubilling.billing.domain.DocumentStatus;
import pe.com.perubilling.billing.domain.DocumentStatusHistoryEntity;
import pe.com.perubilling.shared.domain.DocumentType;
import pe.com.perubilling.billing.infrastructure.DocumentStatusHistoryRepository;
import pe.com.perubilling.billing.infrastructure.ElectronicDocumentRepository;
import pe.com.perubilling.billing.domain.DeliveryStatus;
import pe.com.perubilling.shared.domain.BusinessException;
import pe.com.perubilling.shared.security.TenantContext;
import pe.com.perubilling.voiding.api.VoidDocumentRequest;
import pe.com.perubilling.voiding.api.VoidingResponse;
import pe.com.perubilling.voiding.domain.VoidingBatchEntity;
import pe.com.perubilling.voiding.domain.VoidingBatchStatus;
import pe.com.perubilling.voiding.infrastructure.VoidingBatchRepository;

@Service
public class VoidingService {
    private static final ZoneId LIMA = ZoneId.of("America/Lima");

    private final ElectronicDocumentRepository documents;
    private final DocumentStatusHistoryRepository histories;
    private final VoidingBatchRepository batches;
    private final TenantContext tenantContext;
    private final JdbcClient jdbc;

    public VoidingService(ElectronicDocumentRepository documents,
                          DocumentStatusHistoryRepository histories,
                          VoidingBatchRepository batches,
                          TenantContext tenantContext,
                          JdbcClient jdbc) {
        this.documents = documents;
        this.histories = histories;
        this.batches = batches;
        this.tenantContext = tenantContext;
        this.jdbc = jdbc;
    }

    @Transactional
    public VoidingResponse requestVoid(UUID documentId, VoidDocumentRequest request) {
        UUID tenantId = tenantContext.requireTenantId();
        var document = documents.findByIdAndTenantId(documentId, tenantId)
                .orElseThrow(() -> BusinessException.notFound("DOCUMENT_NOT_FOUND", "Documento no encontrado"));

        if (document.getStatus() != DocumentStatus.ACCEPTED && document.getStatus() != DocumentStatus.OBSERVED) {
            throw BusinessException.conflict("DOCUMENT_NOT_VOIDABLE",
                    "Solo se puede solicitar baja de un comprobante aceptado por SUNAT");
        }
        if (document.getDeliveryStatus() != DeliveryStatus.NOT_DELIVERED) {
            throw BusinessException.conflict("DOCUMENT_ALREADY_GRANTED",
                    "El comprobante ya fue otorgado al adquirente; corresponde evaluar una nota de crédito y no una comunicación de baja");
        }
        if (batches.findByTenantIdAndDocumentId(tenantId, documentId).isPresent()
                || document.getStatus() == DocumentStatus.VOID_REQUESTED
                || document.getStatus() == DocumentStatus.VOIDED) {
            throw BusinessException.conflict("VOID_ALREADY_REQUESTED", "El comprobante ya tiene una baja solicitada");
        }

        String reason = request.reason().trim();
        boolean viaDailySummary = document.getDocumentType() == DocumentType.RECEIPT
                || ((document.getDocumentType() == DocumentType.CREDIT_NOTE
                    || document.getDocumentType() == DocumentType.DEBIT_NOTE)
                    && "03".equals(document.getReferenceDocumentType()));

        if (viaDailySummary) {
            document.setSummaryConditionCode("3");
            document.setStatus(DocumentStatus.PENDING_SUMMARY);
            document.setDailySummaryId(null);
            document.setLastErrorCode(null);
            document.setLastErrorMessage(null);
            documents.save(document);
            history(document.getTenantId(), document.getId(), DocumentStatus.VOID_REQUESTED,
                    "VOID_REQUESTED", "Baja solicitada vía Resumen Diario: " + reason);
            return new VoidingResponse(documentId, DocumentStatus.VOID_REQUESTED, null,
                    "Baja encolada mediante Resumen Diario con estado anulado");
        }

        LocalDate generationDate = LocalDate.now(LIMA);
        acquireSequenceLock(tenantId, document.getIssuerId(), generationDate);
        long sequence = jdbc.sql("""
                SELECT COALESCE(MAX(sequence_number),0)+1
                FROM voiding_batch
                WHERE tenant_id=:tenantId AND issuer_id=:issuerId AND generation_date=:generationDate
                """)
                .param("tenantId", tenantId)
                .param("issuerId", document.getIssuerId())
                .param("generationDate", generationDate)
                .query(Long.class)
                .single();

        VoidingBatchEntity batch = new VoidingBatchEntity();
        batch.setTenantId(tenantId);
        batch.setIssuerId(document.getIssuerId());
        batch.setDocumentId(documentId);
        batch.setReferenceDate(document.getIssueDate());
        batch.setGenerationDate(generationDate);
        batch.setSequenceNumber(sequence);
        batch.setIdentifier("RA-" + generationDate.toString().replace("-", "") + "-" + sequence);
        batch.setReason(reason);
        batch.setStatus(VoidingBatchStatus.QUEUED);
        batches.save(batch);

        document.setStatus(DocumentStatus.VOID_REQUESTED);
        documents.save(document);
        history(tenantId, documentId, DocumentStatus.VOID_REQUESTED, "VOID_REQUESTED",
                "Comunicación de Baja " + batch.getIdentifier() + " encolada");

        return new VoidingResponse(documentId, DocumentStatus.VOID_REQUESTED,
                batch.getIdentifier(), "Comunicación de Baja encolada");
    }

    private void acquireSequenceLock(UUID tenantId, UUID issuerId, LocalDate date) {
        String key = "void:" + tenantId + ":" + issuerId + ":" + date;
        jdbc.sql("SELECT pg_advisory_xact_lock(hashtextextended(:key,0))")
                .param("key", key).query(Long.class).single();
    }

    private void history(UUID tenantId, UUID documentId, DocumentStatus status, String code, String message) {
        var h = new DocumentStatusHistoryEntity();
        h.setTenantId(tenantId);
        h.setDocumentId(documentId);
        h.setStatus(status);
        h.setCode(code);
        h.setMessage(message);
        histories.save(h);
    }
}
