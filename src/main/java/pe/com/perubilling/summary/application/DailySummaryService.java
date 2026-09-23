package pe.com.perubilling.summary.application;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.com.perubilling.billing.domain.DocumentStatus;
import pe.com.perubilling.billing.infrastructure.ElectronicDocumentRepository;
import pe.com.perubilling.issuer.infrastructure.IssuerRepository;
import pe.com.perubilling.shared.domain.BusinessException;
import pe.com.perubilling.shared.security.TenantContext;
import pe.com.perubilling.summary.api.DailySummaryResponse;
import pe.com.perubilling.summary.domain.DailySummaryEntity;
import pe.com.perubilling.summary.domain.DailySummaryStatus;
import pe.com.perubilling.summary.infrastructure.DailySummaryRepository;

@Service
public class DailySummaryService {
    private static final ZoneId LIMA = ZoneId.of("America/Lima");

    private final DailySummaryRepository summaries;
    private final ElectronicDocumentRepository documents;
    private final IssuerRepository issuers;
    private final TenantContext tenant;
    private final JdbcClient jdbc;
    private final int maxSummaryAgeDays;

    public DailySummaryService(
            DailySummaryRepository summaries,
            ElectronicDocumentRepository documents,
            IssuerRepository issuers,
            TenantContext tenant,
            JdbcClient jdbc,
            @Value("${app.sunat.rules.max-summary-age-days:7}") int maxSummaryAgeDays) {
        this.summaries = summaries;
        this.documents = documents;
        this.issuers = issuers;
        this.tenant = tenant;
        this.jdbc = jdbc;
        this.maxSummaryAgeDays = Math.max(1, maxSummaryAgeDays);
    }

    @Transactional
    public DailySummaryResponse create(UUID issuerId, LocalDate referenceDate) {
        return createInternal(tenant.requireTenantId(), issuerId, referenceDate, false)
                .orElseThrow(() -> BusinessException.badRequest(
                        "NO_PENDING_RECEIPTS", "No existen boletas/notas pendientes para la fecha indicada"));
    }

    @Transactional
    public Optional<DailySummaryResponse> createAutomatically(UUID tenantId, UUID issuerId, LocalDate referenceDate) {
        return createInternal(tenantId, issuerId, referenceDate, true);
    }

    private Optional<DailySummaryResponse> createInternal(
            UUID tenantId, UUID issuerId, LocalDate referenceDate, boolean allowEmpty) {
        LocalDate today = LocalDate.now(LIMA);
        LocalDate date = referenceDate == null ? today : referenceDate;
        if (date.isAfter(today)) {
            throw BusinessException.badRequest("INVALID_REFERENCE_DATE", "La fecha de referencia no puede ser futura");
        }

        issuers.findByIdAndTenantId(issuerId, tenantId)
                .filter(pe.com.perubilling.issuer.domain.IssuerEntity::isActive)
                .orElseThrow(() -> BusinessException.notFound("ISSUER_NOT_FOUND", "Emisor no encontrado o inactivo"));

        jdbc.sql("select pg_advisory_xact_lock(hashtext(:k)::bigint)")
                .param("k", tenantId + ":" + issuerId + ":" + date)
                .query((rs, rowNum) -> 1)
                .single();

        var pending = documents.findAllByTenantIdAndIssuerIdAndIssueDateAndStatusOrderByCreatedAtAsc(
                tenantId, issuerId, date, DocumentStatus.PENDING_SUMMARY);
        if (pending.isEmpty()) {
            if (allowEmpty) return Optional.empty();
            throw BusinessException.badRequest("NO_PENDING_RECEIPTS", "No existen boletas/notas pendientes para la fecha indicada");
        }
        if (pending.stream().anyMatch(d -> !"PEN".equals(d.getCurrency()))) {
            throw BusinessException.badRequest("SUMMARY_REQUIRES_PEN", "El Resumen Diario SUNAT debe expresarse en PEN");
        }

        for (var document : pending) {
            if (document.getSummaryConditionCode() == null || "1".equals(document.getSummaryConditionCode())) {
                if (document.getIssueDate().plusDays(maxSummaryAgeDays).isBefore(today)) {
                    throw BusinessException.badRequest(
                            "SUMMARY_DEADLINE_EXCEEDED",
                            "El comprobante " + document.getFullNumber() + " excede el plazo configurado para Resumen Diario");
                }
            } else if ("3".equals(document.getSummaryConditionCode())) {
                if (document.getAcceptedAt() == null) {
                    throw BusinessException.badRequest(
                            "VOID_ACCEPTANCE_DATE_REQUIRED",
                            "No se puede comunicar la anulacion de " + document.getFullNumber() + " sin fecha de aceptacion");
                }
                LocalDate acceptedDate = document.getAcceptedAt().atZone(LIMA).toLocalDate();
                if (acceptedDate.plusDays(maxSummaryAgeDays).isBefore(today)) {
                    throw BusinessException.badRequest(
                            "VOID_SUMMARY_DEADLINE_EXCEEDED",
                            "La anulacion de " + document.getFullNumber() + " excede el plazo configurado");
                }
            }
        }

        Long next = jdbc.sql("""
                select coalesce(max(sequence_number),0)+1
                from daily_summary
                where tenant_id=:t and issuer_id=:i and reference_date=:d
                """)
                .param("t", tenantId)
                .param("i", issuerId)
                .param("d", date)
                .query(Long.class)
                .single();

        DailySummaryEntity summary = new DailySummaryEntity();
        summary.setTenantId(tenantId);
        summary.setIssuerId(issuerId);
        summary.setReferenceDate(date);
        summary.setSequenceNumber(next);
        summary.setIdentifier("RC-" + date.toString().replace("-", "") + "-" + next);
        summary.setStatus(DailySummaryStatus.QUEUED);
        summaries.save(summary);

        for (var document : pending) {
            document.setDailySummaryId(summary.getId());
            document.setStatus(DocumentStatus.SUMMARY_PROCESSING);
            documents.save(document);
        }
        return Optional.of(map(summary));
    }


    @Transactional
    public DailySummaryResponse retryUnknownSubmission(UUID id, boolean confirmRetry) {
        if (!confirmRetry) {
            throw BusinessException.badRequest(
                    "RETRY_CONFIRMATION_REQUIRED",
                    "Debe confirmar explícitamente el reintento de un Resumen Diario con resultado de envío desconocido");
        }
        var summary = summaries.findByIdAndTenantId(id, tenant.requireTenantId())
                .orElseThrow(() -> BusinessException.notFound("SUMMARY_NOT_FOUND", "Resumen Diario no encontrado"));
        if (summary.getStatus() != DailySummaryStatus.SUBMISSION_UNKNOWN) {
            throw BusinessException.conflict(
                    "SUMMARY_RETRY_NOT_ALLOWED",
                    "Solo se puede reintentar manualmente un Resumen Diario en estado SUBMISSION_UNKNOWN");
        }
        summary.setStatus(DailySummaryStatus.RETRY_PENDING);
        summary.setNextRetryAt(java.time.Instant.now());
        summary.setResponseCode("MANUAL_RETRY_REQUESTED");
        summary.setResponseMessage(
                "Reintento manual autorizado. Verifique previamente en SUNAT que el envío anterior no haya sido aceptado.");
        return map(summaries.save(summary));
    }

    @Transactional(readOnly = true)
    public DailySummaryResponse get(UUID id) {
        var summary = summaries.findByIdAndTenantId(id, tenant.requireTenantId())
                .orElseThrow(() -> BusinessException.notFound("SUMMARY_NOT_FOUND", "Resumen Diario no encontrado"));
        return map(summary);
    }

    @Transactional(readOnly = true)
    public Page<DailySummaryResponse> list(int page, int size) {
        return summaries.findAllByTenantIdOrderByCreatedAtDesc(
                tenant.requireTenantId(),
                PageRequest.of(Math.max(0, page), Math.min(100, Math.max(1, size))))
                .map(this::map);
    }

    private DailySummaryResponse map(DailySummaryEntity summary) {
        return new DailySummaryResponse(
                summary.getId(), summary.getIssuerId(), summary.getReferenceDate(), summary.getIdentifier(),
                summary.getStatus(), summary.getTicket(), summary.getResponseCode(), summary.getResponseMessage(),
                summary.getCreatedAt());
    }
}
