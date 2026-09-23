package pe.com.perubilling.billing.application;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.com.perubilling.billing.api.AllowanceChargeResponse;
import pe.com.perubilling.billing.api.CreateDocumentRequest;
import pe.com.perubilling.billing.api.CustomerResponse;
import pe.com.perubilling.billing.api.CustomerRequest;
import pe.com.perubilling.billing.api.DocumentHistoryResponse;
import pe.com.perubilling.billing.api.DocumentSearchCriteria;
import pe.com.perubilling.billing.api.DocumentItemResponse;
import pe.com.perubilling.billing.api.DocumentResponse;
import pe.com.perubilling.billing.api.InstallmentResponse;
import pe.com.perubilling.billing.api.PaymentRequest;
import pe.com.perubilling.billing.api.PaymentResponse;
import pe.com.perubilling.billing.domain.AllowanceChargeEntity;
import pe.com.perubilling.billing.domain.DocumentStatus;
import pe.com.perubilling.billing.domain.DocumentAllowanceChargeEntity;
import pe.com.perubilling.billing.domain.DocumentStatusHistoryEntity;
import pe.com.perubilling.shared.domain.DocumentType;
import pe.com.perubilling.billing.domain.ElectronicDocumentEntity;
import pe.com.perubilling.billing.domain.ElectronicDocumentItemEntity;
import pe.com.perubilling.billing.domain.PaymentInstallmentEntity;
import pe.com.perubilling.billing.domain.PaymentMethod;
import pe.com.perubilling.billing.infrastructure.AllowanceChargeRepository;
import pe.com.perubilling.billing.infrastructure.DocumentStatusHistoryRepository;
import pe.com.perubilling.billing.infrastructure.DocumentAllowanceChargeRepository;
import pe.com.perubilling.billing.infrastructure.ElectronicDocumentItemRepository;
import pe.com.perubilling.billing.infrastructure.ElectronicDocumentRepository;
import pe.com.perubilling.billing.infrastructure.IdempotencyRecordRepository;
import pe.com.perubilling.billing.infrastructure.PaymentInstallmentRepository;
import pe.com.perubilling.billing.tax.TaxCalculation;
import pe.com.perubilling.billing.tax.TaxCalculator;
import pe.com.perubilling.issuer.infrastructure.DocumentSeriesRepository;
import pe.com.perubilling.issuer.infrastructure.IssuerRepository;
import pe.com.perubilling.shared.crypto.HashingService;
import pe.com.perubilling.shared.domain.BusinessException;
import pe.com.perubilling.shared.security.TenantContext;
import tools.jackson.databind.json.JsonMapper;

@Service
public class DocumentService {
    private static final java.time.ZoneId LIMA = java.time.ZoneId.of("America/Lima");
    private final ElectronicDocumentRepository documents;
    private final ElectronicDocumentItemRepository items;
    private final AllowanceChargeRepository allowanceCharges;
    private final DocumentAllowanceChargeRepository documentAllowanceCharges;
    private final PaymentInstallmentRepository installments;
    private final IdempotencyRecordRepository idempotency;
    private final DocumentStatusHistoryRepository histories;
    private final DocumentSeriesRepository seriesRepository;
    private final IssuerRepository issuers;
    private final TenantContext tenantContext;
    private final TaxCalculator taxCalculator;
    private final HashingService hashing;
    private final DocumentRequestValidator requestValidator;
    private final JsonMapper jsonMapper;
    private final JdbcClient jdbc;

    public DocumentService(
            ElectronicDocumentRepository documents,
            ElectronicDocumentItemRepository items,
            AllowanceChargeRepository allowanceCharges,
            DocumentAllowanceChargeRepository documentAllowanceCharges,
            PaymentInstallmentRepository installments,
            IdempotencyRecordRepository idempotency,
            DocumentStatusHistoryRepository histories,
            DocumentSeriesRepository seriesRepository,
            IssuerRepository issuers,
            TenantContext tenantContext,
            TaxCalculator taxCalculator,
            HashingService hashing,
            DocumentRequestValidator requestValidator,
            JsonMapper jsonMapper,
            JdbcClient jdbc) {
        this.documents = documents;
        this.items = items;
        this.allowanceCharges = allowanceCharges;
        this.documentAllowanceCharges = documentAllowanceCharges;
        this.installments = installments;
        this.idempotency = idempotency;
        this.histories = histories;
        this.seriesRepository = seriesRepository;
        this.issuers = issuers;
        this.tenantContext = tenantContext;
        this.taxCalculator = taxCalculator;
        this.hashing = hashing;
        this.requestValidator = requestValidator;
        this.jsonMapper = jsonMapper;
        this.jdbc = jdbc;
    }

    @Transactional
    public DocumentResponse create(DocumentType type, CreateDocumentRequest request, String idempotencyKey) {
        UUID tenantId = tenantContext.requireTenantId();
        String normalizedKey = normalizeIdempotencyKey(idempotencyKey);
        String requestHash = hashRequest(type, request);

        if (normalizedKey != null) {
            acquireIdempotencyLock(tenantId, normalizedKey);
            var previous = idempotency.findByTenantIdAndIdempotencyKey(tenantId, normalizedKey);
            if (previous.isPresent()) {
                if (!previous.get().getRequestHash().equals(requestHash)) {
                    throw BusinessException.conflict("IDEMPOTENCY_KEY_REUSED",
                            "La misma Idempotency-Key fue utilizada con un contenido diferente");
                }
                return get(previous.get().getResourceId());
            }
        }

        var issuer = issuers.findByIdAndTenantId(request.issuerId(), tenantId)
                .orElseThrow(() -> BusinessException.notFound("ISSUER_NOT_FOUND", "Emisor no encontrado"));
        if (!issuer.isActive()) {
            throw BusinessException.badRequest("ISSUER_INACTIVE", "El emisor está inactivo");
        }

        String externalId = normalizeExternalId(request.externalId());
        if (externalId != null && documents.findByTenantIdAndIssuerIdAndExternalId(tenantId, issuer.getId(), externalId).isPresent()) {
            throw BusinessException.conflict("EXTERNAL_ID_ALREADY_EXISTS",
                    "externalId ya está asociado a otro comprobante del mismo emisor");
        }

        LocalDate issueDate = request.issueDate() == null ? LocalDate.now(LIMA) : request.issueDate();
        LocalTime issueTime = request.issueTime() == null
                ? LocalTime.now(LIMA).truncatedTo(ChronoUnit.SECONDS)
                : request.issueTime().truncatedTo(ChronoUnit.SECONDS);
        TaxCalculation calculation = taxCalculator.calculate(request.items(), request.safeAdjustments(), issueDate);
        requestValidator.validate(type, request, issueDate, tenantId, calculation);

        String seriesValue = request.series().toUpperCase(Locale.ROOT);
        var series = seriesRepository.findForUpdate(tenantId, issuer.getId(), type, seriesValue)
                .orElseThrow(() -> BusinessException.badRequest("SERIES_NOT_CONFIGURED",
                        "La serie no existe o no está activa para este tipo de documento"));
        if (!series.isActive()) {
            throw BusinessException.badRequest("SERIES_INACTIVE", "La serie está inactiva");
        }
        long correlativo = series.getCurrentValue() + 1;
        series.setCurrentValue(correlativo);

        PaymentRequest payment = requestValidator.normalizePayment(type, request.payment(), calculation.total(), issueDate);

        ElectronicDocumentEntity entity = new ElectronicDocumentEntity();
        entity.setTenantId(tenantId);
        entity.setIssuerId(issuer.getId());
        entity.setExternalId(externalId);
        entity.setDocumentType(type);
        entity.setSeries(seriesValue);
        entity.setCorrelativo(correlativo);
        entity.setFullNumber(seriesValue + "-" + correlativo);
        entity.setIssueDate(issueDate);
        entity.setIssueTime(issueTime);
        entity.setOperationType(request.operationType() == null || request.operationType().isBlank()
                ? "0101" : request.operationType());
        entity.setCurrency(request.currency().toUpperCase(Locale.ROOT));
        CustomerRequest customer = request.customer() == null ? anonymousCustomer() : request.customer();
        entity.setCustomerDocumentType(customer.documentType());
        entity.setCustomerDocumentNumber(customer.documentNumber().trim());
        entity.setCustomerName(customer.name().trim());
        entity.setCustomerAddress(trimToNull(customer.address()));
        entity.setCustomerEmail(trimToNull(customer.email()));
        entity.setCustomerCountryCode(normalizeUpper(customer.countryCode()));

        if (request.reference() != null) {
            entity.setReferenceDocumentType(request.reference().documentType());
            entity.setReferenceDocumentNumber(request.reference().number().toUpperCase(Locale.ROOT));
            entity.setReasonCode(request.reference().reasonCode());
            entity.setReasonText(request.reference().reason().trim());
        }

        entity.setTaxableAmount(calculation.taxable());
        entity.setIvapTaxableAmount(calculation.ivapTaxable());
        entity.setExoneratedAmount(calculation.exonerated());
        entity.setUnaffectedAmount(calculation.unaffected());
        entity.setExportAmount(calculation.exportAmount());
        entity.setFreeAmount(calculation.free());
        entity.setIgvAmount(calculation.igv());
        entity.setIvapAmount(calculation.ivap());
        entity.setFreeTaxAmount(calculation.freeTax());
        entity.setIcbperAmount(calculation.icbper());
        entity.setAllowanceTotalAmount(calculation.allowanceTotal());
        entity.setChargeTotalAmount(calculation.chargeTotal());
        entity.setTotalAmount(calculation.total());

        if (payment != null) {
            entity.setPaymentMethod(payment.method());
            entity.setPendingAmount(payment.pendingAmount());
        }

        DocumentStatus initialStatus = requestValidator.requiresDailySummary(type, request)
                ? DocumentStatus.SUMMARY_PREPARATION_QUEUED
                : DocumentStatus.QUEUED;
        entity.setStatus(initialStatus);
        documents.save(entity);

        for (TaxCalculation.CalculatedItem calculated : calculation.items()) {
            ElectronicDocumentItemEntity item = new ElectronicDocumentItemEntity();
            item.setTenantId(tenantId);
            item.setDocumentId(entity.getId());
            item.setLineNumber(calculated.lineNumber());
            item.setSku(trimToNull(calculated.source().sku()));
            item.setSunatProductCode(trimToNull(calculated.source().sunatProductCode()));
            item.setGtin(trimToNull(calculated.source().gtin()));
            item.setGtinSchemeId(trimToNull(calculated.source().gtinSchemeId()));
            item.setDescription(calculated.source().description().trim());
            item.setUnitCode(calculated.source().unitCode().toUpperCase(Locale.ROOT));
            item.setQuantity(calculated.source().quantity());
            item.setUnitValue(calculated.source().unitValue());
            item.setUnitPrice(calculated.unitPrice());
            item.setTaxAffectationCode(calculated.source().taxAffectationCode());
            item.setIgvRate(calculated.appliedTaxRate());
            item.setLineGrossAmount(calculated.lineGross());
            item.setLineAllowanceAmount(calculated.lineAllowance());
            item.setLineChargeAmount(calculated.lineCharge());
            item.setLineBaseAmount(calculated.lineBase());
            item.setLineIgvAmount(calculated.lineTax());
            item.setIcbperPerUnit(calculated.appliedIcbperPerUnit());
            item.setLineIcbperAmount(calculated.lineIcbper());
            item.setLineTotalAmount(calculated.lineTotal());
            item.setFreeOperation(calculated.freeOperation());
            items.save(item);

            for (TaxCalculation.CalculatedAdjustment adjustment : calculated.adjustments()) {
                AllowanceChargeEntity persistedAdjustment = new AllowanceChargeEntity();
                persistedAdjustment.setTenantId(tenantId);
                persistedAdjustment.setDocumentId(entity.getId());
                persistedAdjustment.setDocumentItemId(item.getId());
                persistedAdjustment.setLineNumber(item.getLineNumber());
                persistedAdjustment.setSequenceNumber(adjustment.sequenceNumber());
                persistedAdjustment.setCharge(adjustment.charge());
                persistedAdjustment.setReasonCode(adjustment.reasonCode());
                persistedAdjustment.setFactor(adjustment.factor());
                persistedAdjustment.setAmount(adjustment.amount());
                persistedAdjustment.setBaseAmount(adjustment.baseAmount());
                allowanceCharges.save(persistedAdjustment);
            }
        }

        for (TaxCalculation.CalculatedAdjustment adjustment : calculation.documentAdjustments()) {
            DocumentAllowanceChargeEntity persistedAdjustment = new DocumentAllowanceChargeEntity();
            persistedAdjustment.setTenantId(tenantId);
            persistedAdjustment.setDocumentId(entity.getId());
            persistedAdjustment.setSequenceNumber(adjustment.sequenceNumber());
            persistedAdjustment.setCharge(adjustment.charge());
            persistedAdjustment.setReasonCode(adjustment.reasonCode());
            persistedAdjustment.setFactor(adjustment.factor());
            persistedAdjustment.setAmount(adjustment.amount());
            persistedAdjustment.setBaseAmount(adjustment.baseAmount());
            documentAllowanceCharges.save(persistedAdjustment);
        }

        if (payment != null && payment.method() == PaymentMethod.CREDITO) {
            int number = 1;
            for (var source : payment.installments()) {
                PaymentInstallmentEntity installment = new PaymentInstallmentEntity();
                installment.setTenantId(tenantId);
                installment.setDocumentId(entity.getId());
                installment.setInstallmentNumber(number++);
                installment.setDueDate(source.dueDate());
                installment.setAmount(money(source.amount()));
                installments.save(installment);
            }
        }

        addHistory(entity, initialStatus, "CREATED",
                initialStatus == DocumentStatus.SUMMARY_PREPARATION_QUEUED
                        ? "Documento creado y encolado para preparar artefactos antes del Resumen Diario SUNAT"
                        : "Documento creado y encolado para procesamiento");

        if (normalizedKey != null) {
            documents.flush();
            insertIdempotencyRecord(tenantId, normalizedKey, requestHash, entity.getId());
        }

        return toResponse(entity, loadItems(entity), loadInstallments(entity));
    }

    @Transactional(readOnly = true)
    public DocumentResponse get(UUID documentId) {
        ElectronicDocumentEntity document = requireDocument(documentId);
        return toResponse(document, loadItems(document), loadInstallments(document));
    }

    @Transactional(readOnly = true)
    public Page<DocumentResponse> list(DocumentSearchCriteria criteria, int page, int size) {
        UUID tenantId = tenantContext.requireTenantId();
        if (criteria.fromIssueDate() != null && criteria.toIssueDate() != null
                && criteria.fromIssueDate().isAfter(criteria.toIssueDate())) {
            throw BusinessException.badRequest("INVALID_DATE_RANGE", "fromIssueDate no puede ser posterior a toIssueDate");
        }
        int safeSize = Math.min(Math.max(size, 1), 100);
        var pageable = PageRequest.of(Math.max(page, 0), safeSize);
        Page<ElectronicDocumentEntity> result = documents.search(
                tenantId,
                criteria.issuerId(),
                criteria.documentType(),
                normalize(criteria.operationType()),
                normalizeUpper(criteria.series()),
                normalizeUpper(criteria.number()),
                normalizeExternalId(criteria.externalId()),
                criteria.status(),
                normalize(criteria.customerDocumentNumber()),
                criteria.fromIssueDate(),
                criteria.toIssueDate(),
                pageable);
        return result.map(doc -> toResponse(doc, List.of(), loadInstallments(doc)));
    }

    @Transactional
    public DocumentResponse requestReconciliation(UUID documentId) {
        ElectronicDocumentEntity document = requireDocument(documentId);
        if (document.getStatus() != DocumentStatus.SUBMISSION_UNKNOWN
                && document.getStatus() != DocumentStatus.RECONCILIATION_REQUIRED) {
            throw BusinessException.conflict("RECONCILIATION_NOT_ALLOWED",
                    "Solo se puede reconciliar un documento cuyo resultado SUNAT sea desconocido");
        }
        document.setStatus(DocumentStatus.SUBMISSION_UNKNOWN);
        document.setNextRetryAt(java.time.Instant.now());
        document.setLastErrorCode(null);
        document.setLastErrorMessage(null);
        documents.save(document);
        addHistory(document, DocumentStatus.SUBMISSION_UNKNOWN, "MANUAL_RECONCILIATION",
                "Reconciliación SUNAT solicitada manualmente; no se realizará reenvío ciego");
        return toResponse(document, loadItems(document), loadInstallments(document));
    }

    @Transactional(readOnly = true)
    public List<DocumentHistoryResponse> history(UUID documentId) {
        ElectronicDocumentEntity document = requireDocument(documentId);
        return histories.findAllByTenantIdAndDocumentIdOrderByCreatedAtAsc(
                        document.getTenantId(), documentId).stream()
                .map(h -> new DocumentHistoryResponse(h.getStatus(), h.getCode(), h.getMessage(), h.getCreatedAt()))
                .toList();
    }

    public ElectronicDocumentEntity requireDocument(UUID id) {
        return documents.findByIdAndTenantId(id, tenantContext.requireTenantId())
                .orElseThrow(() -> BusinessException.notFound("DOCUMENT_NOT_FOUND", "Documento no encontrado"));
    }

    public void addHistory(ElectronicDocumentEntity document, DocumentStatus status, String code, String message) {
        DocumentStatusHistoryEntity history = new DocumentStatusHistoryEntity();
        history.setTenantId(document.getTenantId());
        history.setDocumentId(document.getId());
        history.setStatus(status);
        history.setCode(code);
        history.setMessage(message);
        histories.save(history);
    }

    private List<ElectronicDocumentItemEntity> loadItems(ElectronicDocumentEntity document) {
        return items.findAllByTenantIdAndDocumentIdOrderByLineNumber(document.getTenantId(), document.getId());
    }

    private List<PaymentInstallmentEntity> loadInstallments(ElectronicDocumentEntity document) {
        return installments.findAllByTenantIdAndDocumentIdOrderByInstallmentNumber(
                document.getTenantId(), document.getId());
    }

    private void acquireIdempotencyLock(UUID tenantId, String key) {
        String lockKey = tenantId + ":" + key;
        jdbc.sql("SELECT pg_advisory_xact_lock(hashtextextended(:lockKey, 0))")
                .param("lockKey", lockKey)
                .query(Long.class)
                .single();
    }

    private void insertIdempotencyRecord(UUID tenantId, String key, String requestHash, UUID resourceId) {
        jdbc.sql("""
                INSERT INTO idempotency_record
                    (id, tenant_id, idempotency_key, request_hash, resource_id, created_at, updated_at)
                VALUES
                    (:id, :tenantId, :key, :requestHash, :resourceId, now(), now())
                """)
                .param("id", UUID.randomUUID())
                .param("tenantId", tenantId)
                .param("key", key)
                .param("requestHash", requestHash)
                .param("resourceId", resourceId)
                .update();
    }

    private String normalizeExternalId(String value) {
        String normalized = normalize(value);
        if (normalized == null) return null;
        if (normalized.length() > 100) {
            throw BusinessException.badRequest("EXTERNAL_ID_TOO_LONG", "externalId no puede superar 100 caracteres");
        }
        return normalized;
    }

    private String normalizeUpper(String value) {
        String normalized = normalize(value);
        return normalized == null ? null : normalized.toUpperCase(Locale.ROOT);
    }

    private String normalize(String value) {
        if (value == null || value.isBlank()) return null;
        return value.trim();
    }

    private String normalizeIdempotencyKey(String key) {
        if (key == null || key.isBlank()) return null;
        String normalized = key.trim();
        if (normalized.length() > 150) {
            throw BusinessException.badRequest("IDEMPOTENCY_KEY_TOO_LONG",
                    "Idempotency-Key no puede superar 150 caracteres");
        }
        return normalized;
    }

    private String hashRequest(DocumentType type, CreateDocumentRequest request) {
        try {
            return hashing.sha256(type.name() + ":" + new String(
                    jsonMapper.writeValueAsBytes(request), java.nio.charset.StandardCharsets.UTF_8));
        } catch (Exception ex) {
            throw new IllegalStateException("No se pudo calcular el hash de idempotencia", ex);
        }
    }

    private DocumentResponse toResponse(
            ElectronicDocumentEntity d,
            List<ElectronicDocumentItemEntity> itemEntities,
            List<PaymentInstallmentEntity> installmentEntities) {
        List<AllowanceChargeEntity> adjustmentEntities = itemEntities.isEmpty()
                ? List.of()
                : allowanceCharges.findAllByTenantIdAndDocumentIdOrderByLineNumberAscSequenceNumberAsc(
                        d.getTenantId(), d.getId());

        List<DocumentItemResponse> responseItems = itemEntities.stream()
                .map(i -> new DocumentItemResponse(
                        i.getLineNumber(), i.getSku(), i.getDescription(), i.getUnitCode(), i.getQuantity(),
                        i.getUnitValue(), i.getUnitPrice(), i.getTaxAffectationCode(), i.getIgvRate(),
                        i.getLineGrossAmount(), i.getLineAllowanceAmount(), i.getLineChargeAmount(),
                        i.getLineBaseAmount(), i.getLineIgvAmount(), i.getLineIcbperAmount(),
                        i.getLineTotalAmount(), i.isFreeOperation(),
                        adjustmentEntities.stream()
                                .filter(adjustment -> adjustment.getDocumentItemId().equals(i.getId()))
                                .map(adjustment -> new AllowanceChargeResponse(
                                        adjustment.getSequenceNumber(),
                                        adjustment.isCharge(),
                                        adjustment.getReasonCode(),
                                        adjustment.getFactor(),
                                        adjustment.getAmount(),
                                        adjustment.getBaseAmount()))
                                .toList()))
                .toList();

        List<AllowanceChargeResponse> documentAdjustments = documentAllowanceCharges
                .findAllByTenantIdAndDocumentIdOrderBySequenceNumberAsc(d.getTenantId(), d.getId())
                .stream()
                .map(adjustment -> new AllowanceChargeResponse(
                        adjustment.getSequenceNumber(),
                        adjustment.isCharge(),
                        adjustment.getReasonCode(),
                        adjustment.getFactor(),
                        adjustment.getAmount(),
                        adjustment.getBaseAmount()))
                .toList();

        PaymentResponse payment = d.getPaymentMethod() == null ? null : new PaymentResponse(
                d.getPaymentMethod(),
                d.getPendingAmount(),
                installmentEntities.stream()
                        .map(i -> new InstallmentResponse(
                                i.getInstallmentNumber(), i.getDueDate(), i.getAmount()))
                        .toList());

        CustomerResponse customer = new CustomerResponse(
                d.getCustomerDocumentType(), d.getCustomerDocumentNumber(), d.getCustomerName(),
                d.getCustomerAddress(), d.getCustomerEmail(), d.getCustomerCountryCode());

        return new DocumentResponse(
                d.getId(), d.getIssuerId(), d.getExternalId(), d.getDocumentType(), d.getFullNumber(),
                d.getOperationType(), customer, d.getIssueDate(), d.getIssueTime(), d.getCurrency(), d.getStatus(),
                d.getTaxableAmount(), d.getIvapTaxableAmount(), d.getExoneratedAmount(),
                d.getUnaffectedAmount(), d.getExportAmount(), d.getFreeAmount(),
                d.getIgvAmount(), d.getIvapAmount(), d.getFreeTaxAmount(), d.getIcbperAmount(),
                d.getAllowanceTotalAmount(), d.getChargeTotalAmount(), d.getTotalAmount(),
                payment, d.getCdrCode(), d.getCdrDescription(),
                d.getLastErrorCode(), d.getLastErrorMessage(), d.getDeliveryStatus(), d.getGrantedChannel(),
                d.getGrantedAt(), d.getCreatedAt(), d.getAcceptedAt(), documentAdjustments, responseItems);
    }

    private CustomerRequest anonymousCustomer() {
        return new CustomerRequest("0", "-", "CONSUMIDOR FINAL", null, null);
    }

    private BigDecimal money(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP);
    }

    private String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
