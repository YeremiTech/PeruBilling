package pe.com.perubilling.voiding.application;

import java.time.Instant;
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
        var document = documents.findByIdAndTenantIdForUpdate(documentId, tenantId)
                .orElseThrow(() -> BusinessException.notFound("DOCUMENT_NOT_FOUND", "Documento no encontrado"));

        var existingBatch = batches.findByTenantIdAndDocumentId(tenantId, documentId);
        if (existingBatch.isPresent()) {
            var batch = existingBatch.get();
            DocumentStatus status = document.getStatus() == DocumentStatus.VOIDED
                    ? DocumentStatus.VOIDED
                    : DocumentStatus.VOID_REQUESTED;
            return new VoidingResponse(documentId, status, batch.getIdentifier(),
                    status == DocumentStatus.VOIDED
                            ? "El comprobante ya fue dado de baja"
                            : "La Comunicación de Baja ya se encuentra registrada");
        }
        if (document.getStatus() == DocumentStatus.VOID_REQUESTED
                || document.getStatus() == DocumentStatus.VOIDED
                || (document.getStatus() == DocumentStatus.PENDING_SUMMARY
                    && "3".equals(document.getSummaryConditionCode()))) {
            return new VoidingResponse(
                    documentId,
                    document.getStatus() == DocumentStatus.VOIDED ? DocumentStatus.VOIDED : DocumentStatus.VOID_REQUESTED,
                    null,
                    document.getStatus() == DocumentStatus.VOIDED
                            ? "El comprobante ya fue dado de baja"
                            : "La baja del comprobante ya fue solicitada");
        }

        if (document.getStatus() != DocumentStatus.ACCEPTED && document.getStatus() != DocumentStatus.OBSERVED) {
            throw BusinessException.conflict("DOCUMENT_NOT_VOIDABLE",
                    "Solo se puede solicitar baja de un comprobante aceptado por SUNAT");
        }
        if (document.getDeliveryStatus() != null && document.getDeliveryStatus() != DeliveryStatus.NOT_DELIVERED) {
            throw BusinessException.conflict("DOCUMENT_ALREADY_GRANTED",
                    "El comprobante ya fue otorgado al adquirente; corresponde evaluar una nota de crédito y no una comunicación de baja");
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

        String identifier = "RA-" + generationDate.toString().replace("-", "") + "-" + sequence;
        UUID batchId = UUID.randomUUID();
        Instant now = Instant.now();
        int inserted = jdbc.sql("""
                INSERT INTO voiding_batch
                    (id, tenant_id, issuer_id, document_id, reference_date, generation_date,
                     sequence_number, identifier, reason, status, attempt_count, created_at, updated_at)
                VALUES
                    (:id, :tenantId, :issuerId, :documentId, :referenceDate, :generationDate,
                     :sequenceNumber, :identifier, :reason, :status, 0, :createdAt, :updatedAt)
                ON CONFLICT (tenant_id, document_id) DO NOTHING
                """)
                .param("id", batchId)
                .param("tenantId", tenantId)
                .param("issuerId", document.getIssuerId())
                .param("documentId", documentId)
                .param("referenceDate", document.getIssueDate())
                .param("generationDate", generationDate)
                .param("sequenceNumber", sequence)
                .param("identifier", identifier)
                .param("reason", reason)
                .param("status", VoidingBatchStatus.QUEUED.name())
                .param("createdAt", now)
                .param("updatedAt", now)
                .update();

        if (inserted == 0) {
            var existing = batches.findByTenantIdAndDocumentId(tenantId, documentId)
                    .orElseThrow(() -> BusinessException.conflict(
                            "VOID_ALREADY_REQUESTED",
                            "El documento ya tiene una solicitud de baja registrada"));
            return new VoidingResponse(documentId, DocumentStatus.VOID_REQUESTED,
                    existing.getIdentifier(), "La Comunicación de Baja ya se encuentra registrada");
        }

        document.setStatus(DocumentStatus.VOID_REQUESTED);
        documents.save(document);
        history(tenantId, documentId, DocumentStatus.VOID_REQUESTED, "VOID_REQUESTED",
                "Comunicación de Baja " + identifier + " encolada");

        return new VoidingResponse(documentId, DocumentStatus.VOID_REQUESTED,
                identifier, "Comunicación de Baja encolada");
    }

    @Transactional
    public VoidingResponse retryUnknownSubmission(UUID documentId, boolean confirmRetry) {
        if (!confirmRetry) {
            throw BusinessException.badRequest(
                    "RETRY_CONFIRMATION_REQUIRED",
                    "Debe confirmar explícitamente el reintento de una Comunicación de Baja con resultado de envío desconocido");
        }
        UUID tenantId = tenantContext.requireTenantId();
        var document = documents.findByIdAndTenantIdForUpdate(documentId, tenantId)
                .orElseThrow(() -> BusinessException.notFound("DOCUMENT_NOT_FOUND", "Documento no encontrado"));
        var batch = batches.findByTenantIdAndDocumentId(tenantId, documentId)
                .orElseThrow(() -> BusinessException.notFound("VOIDING_NOT_FOUND", "Comunicación de Baja no encontrada"));
        if (batch.getStatus() != VoidingBatchStatus.SUBMISSION_UNKNOWN) {
            throw BusinessException.conflict(
                    "VOIDING_RETRY_NOT_ALLOWED",
                    "Solo se puede reintentar manualmente una Comunicación de Baja en estado SUBMISSION_UNKNOWN");
        }
        batch.setStatus(VoidingBatchStatus.RETRY_PENDING);
        batch.setNextRetryAt(Instant.now());
        batch.setResponseCode("MANUAL_RETRY_REQUESTED");
        batch.setResponseMessage(
                "Reintento manual autorizado. Verifique previamente en SUNAT que el envío anterior no haya sido aceptado.");
        batches.save(batch);
        return new VoidingResponse(documentId, document.getStatus(), batch.getIdentifier(),
                "Comunicación de Baja reencolada para reintento manual controlado");
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
