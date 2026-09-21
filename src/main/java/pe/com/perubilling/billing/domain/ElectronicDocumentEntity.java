package pe.com.perubilling.billing.domain;

import pe.com.perubilling.shared.domain.DocumentType;
import pe.com.perubilling.billing.domain.DeliveryChannel;
import pe.com.perubilling.billing.domain.DeliveryStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;
import pe.com.perubilling.shared.domain.BaseEntity;

@Entity
@Table(name = "electronic_document")
public class ElectronicDocumentEntity extends BaseEntity {
    @Column(name = "tenant_id", nullable = false) private UUID tenantId;
    @Column(name = "issuer_id", nullable = false) private UUID issuerId;
    @Column(name = "external_id", length = 100) private String externalId;
    @Enumerated(EnumType.STRING) @Column(name = "document_type", nullable = false, length = 30) private DocumentType documentType;
    @Column(nullable = false, length = 4) private String series;
    @Column(nullable = false) private long correlativo;
    @Column(name = "full_number", nullable = false, length = 30) private String fullNumber;
    @Column(name = "issue_date", nullable = false) private LocalDate issueDate;
    @Column(name = "issue_time", nullable = false) private LocalTime issueTime;
    @Column(name = "operation_type", nullable = false, length = 4) private String operationType = "0101";
    @Column(nullable = false, length = 3) private String currency = "PEN";
    @Column(name = "customer_document_type", nullable = false, length = 2) private String customerDocumentType;
    @Column(name = "customer_document_number", nullable = false, length = 20) private String customerDocumentNumber;
    @Column(name = "customer_name", nullable = false, length = 300) private String customerName;
    @Column(name = "customer_address", length = 300) private String customerAddress;
    @Column(name = "customer_email", length = 255) private String customerEmail;
    @Column(name = "customer_country_code", length = 2) private String customerCountryCode;
    @Column(name = "reference_document_type", length = 2) private String referenceDocumentType;
    @Column(name = "reference_document_number", length = 30) private String referenceDocumentNumber;
    @Column(name = "reason_code", length = 4) private String reasonCode;
    @Column(name = "reason_text", length = 500) private String reasonText;

    @Column(name = "taxable_amount", nullable = false, precision = 18, scale = 2) private BigDecimal taxableAmount = BigDecimal.ZERO;
    @Column(name = "ivap_taxable_amount", nullable = false, precision = 18, scale = 2) private BigDecimal ivapTaxableAmount = BigDecimal.ZERO;
    @Column(name = "exonerated_amount", nullable = false, precision = 18, scale = 2) private BigDecimal exoneratedAmount = BigDecimal.ZERO;
    @Column(name = "unaffected_amount", nullable = false, precision = 18, scale = 2) private BigDecimal unaffectedAmount = BigDecimal.ZERO;
    @Column(name = "export_amount", nullable = false, precision = 18, scale = 2) private BigDecimal exportAmount = BigDecimal.ZERO;
    @Column(name = "free_amount", nullable = false, precision = 18, scale = 2) private BigDecimal freeAmount = BigDecimal.ZERO;
    @Column(name = "igv_amount", nullable = false, precision = 18, scale = 2) private BigDecimal igvAmount = BigDecimal.ZERO;
    @Column(name = "ivap_amount", nullable = false, precision = 18, scale = 2) private BigDecimal ivapAmount = BigDecimal.ZERO;
    @Column(name = "free_tax_amount", nullable = false, precision = 18, scale = 2) private BigDecimal freeTaxAmount = BigDecimal.ZERO;
    @Column(name = "icbper_amount", nullable = false, precision = 18, scale = 2) private BigDecimal icbperAmount = BigDecimal.ZERO;
    @Column(name = "allowance_total_amount", nullable = false, precision = 18, scale = 2) private BigDecimal allowanceTotalAmount = BigDecimal.ZERO;
    @Column(name = "charge_total_amount", nullable = false, precision = 18, scale = 2) private BigDecimal chargeTotalAmount = BigDecimal.ZERO;
    @Column(name = "total_amount", nullable = false, precision = 18, scale = 2) private BigDecimal totalAmount = BigDecimal.ZERO;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_method", length = 20)
    private PaymentMethod paymentMethod;
    @Column(name = "pending_amount", precision = 18, scale = 2)
    private BigDecimal pendingAmount;

    @Column(name = "summary_condition_code", nullable = false, length = 1)
    private String summaryConditionCode = "1";

    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 30) private DocumentStatus status = DocumentStatus.QUEUED;
    @Column(name = "processing_started_at") private Instant processingStartedAt;
    @Column(name = "processing_lease_until") private Instant processingLeaseUntil;
    @Column(name = "attempt_count", nullable = false) private int attemptCount;
    @Column(name = "next_retry_at") private Instant nextRetryAt;
    @Column(name = "last_error_code", length = 100) private String lastErrorCode;
    @Column(name = "last_error_message", length = 2000) private String lastErrorMessage;
    @Column(name = "xml_path", length = 1000) private String xmlPath;
    @Column(name = "signed_xml_path", length = 1000) private String signedXmlPath;
    @Column(name = "pdf_path", length = 1000) private String pdfPath;
    @Column(name = "cdr_path", length = 1000) private String cdrPath;
    @Column(name = "xml_hash", length = 64) private String xmlHash;
    @Column(name = "cdr_code", length = 20) private String cdrCode;
    @Column(name = "cdr_description", length = 2000) private String cdrDescription;
    @Column(name = "accepted_at") private Instant acceptedAt;
    @Column(name = "daily_summary_id") private UUID dailySummaryId;

    @Enumerated(EnumType.STRING)
    @Column(name = "delivery_status", nullable = false, length = 30)
    private DeliveryStatus deliveryStatus = DeliveryStatus.NOT_DELIVERED;
    @Column(name = "granted_at") private Instant grantedAt;
    @Enumerated(EnumType.STRING)
    @Column(name = "granted_channel", length = 30)
    private DeliveryChannel grantedChannel;
    @Column(name = "submission_unknown_since") private Instant submissionUnknownSince;
    @Column(name = "reconciliation_count", nullable = false) private int reconciliationCount;

    public UUID getTenantId() { return tenantId; } public void setTenantId(UUID v) { tenantId=v; }
    public UUID getIssuerId() { return issuerId; } public void setIssuerId(UUID v) { issuerId=v; }
    public String getExternalId() { return externalId; } public void setExternalId(String v) { externalId=v; }
    public DocumentType getDocumentType() { return documentType; } public void setDocumentType(DocumentType v) { documentType=v; }
    public String getSeries() { return series; } public void setSeries(String v) { series=v; }
    public long getCorrelativo() { return correlativo; } public void setCorrelativo(long v) { correlativo=v; }
    public String getFullNumber() { return fullNumber; } public void setFullNumber(String v) { fullNumber=v; }
    public LocalDate getIssueDate() { return issueDate; } public void setIssueDate(LocalDate v) { issueDate=v; }
    public LocalTime getIssueTime() { return issueTime; } public void setIssueTime(LocalTime v) { issueTime=v; }
    public String getOperationType() { return operationType; } public void setOperationType(String v) { operationType=v; }
    public String getCurrency() { return currency; } public void setCurrency(String v) { currency=v; }
    public String getCustomerDocumentType() { return customerDocumentType; } public void setCustomerDocumentType(String v) { customerDocumentType=v; }
    public String getCustomerDocumentNumber() { return customerDocumentNumber; } public void setCustomerDocumentNumber(String v) { customerDocumentNumber=v; }
    public String getCustomerName() { return customerName; } public void setCustomerName(String v) { customerName=v; }
    public String getCustomerAddress() { return customerAddress; } public void setCustomerAddress(String v) { customerAddress=v; }
    public String getCustomerEmail() { return customerEmail; } public void setCustomerEmail(String v) { customerEmail=v; }
    public String getCustomerCountryCode() { return customerCountryCode; } public void setCustomerCountryCode(String v) { customerCountryCode=v; }
    public String getReferenceDocumentType() { return referenceDocumentType; } public void setReferenceDocumentType(String v) { referenceDocumentType=v; }
    public String getReferenceDocumentNumber() { return referenceDocumentNumber; } public void setReferenceDocumentNumber(String v) { referenceDocumentNumber=v; }
    public String getReasonCode() { return reasonCode; } public void setReasonCode(String v) { reasonCode=v; }
    public String getReasonText() { return reasonText; } public void setReasonText(String v) { reasonText=v; }
    public BigDecimal getTaxableAmount() { return taxableAmount; } public void setTaxableAmount(BigDecimal v) { taxableAmount=v; }
    public BigDecimal getIvapTaxableAmount() { return ivapTaxableAmount; } public void setIvapTaxableAmount(BigDecimal v) { ivapTaxableAmount=v; }
    public BigDecimal getExoneratedAmount() { return exoneratedAmount; } public void setExoneratedAmount(BigDecimal v) { exoneratedAmount=v; }
    public BigDecimal getUnaffectedAmount() { return unaffectedAmount; } public void setUnaffectedAmount(BigDecimal v) { unaffectedAmount=v; }
    public BigDecimal getExportAmount() { return exportAmount; } public void setExportAmount(BigDecimal v) { exportAmount=v; }
    public BigDecimal getFreeAmount() { return freeAmount; } public void setFreeAmount(BigDecimal v) { freeAmount=v; }
    public BigDecimal getIgvAmount() { return igvAmount; } public void setIgvAmount(BigDecimal v) { igvAmount=v; }
    public BigDecimal getIvapAmount() { return ivapAmount; } public void setIvapAmount(BigDecimal v) { ivapAmount=v; }
    public BigDecimal getFreeTaxAmount() { return freeTaxAmount; } public void setFreeTaxAmount(BigDecimal v) { freeTaxAmount=v; }
    public BigDecimal getIcbperAmount() { return icbperAmount; } public void setIcbperAmount(BigDecimal v) { icbperAmount=v; }
    public BigDecimal getAllowanceTotalAmount() { return allowanceTotalAmount; } public void setAllowanceTotalAmount(BigDecimal v) { allowanceTotalAmount=v; }
    public BigDecimal getChargeTotalAmount() { return chargeTotalAmount; } public void setChargeTotalAmount(BigDecimal v) { chargeTotalAmount=v; }
    public BigDecimal getTotalAmount() { return totalAmount; } public void setTotalAmount(BigDecimal v) { totalAmount=v; }
    public PaymentMethod getPaymentMethod() { return paymentMethod; } public void setPaymentMethod(PaymentMethod v) { paymentMethod=v; }
    public BigDecimal getPendingAmount() { return pendingAmount; } public void setPendingAmount(BigDecimal v) { pendingAmount=v; }
    public String getSummaryConditionCode() { return summaryConditionCode; } public void setSummaryConditionCode(String v) { summaryConditionCode=v; }
    public DocumentStatus getStatus() { return status; } public void setStatus(DocumentStatus v) { status=v; }
    public Instant getProcessingStartedAt() { return processingStartedAt; } public void setProcessingStartedAt(Instant v) { processingStartedAt=v; }
    public Instant getProcessingLeaseUntil() { return processingLeaseUntil; } public void setProcessingLeaseUntil(Instant v) { processingLeaseUntil=v; }
    public int getAttemptCount() { return attemptCount; } public void setAttemptCount(int v) { attemptCount=v; }
    public Instant getNextRetryAt() { return nextRetryAt; } public void setNextRetryAt(Instant v) { nextRetryAt=v; }
    public String getLastErrorCode() { return lastErrorCode; } public void setLastErrorCode(String v) { lastErrorCode=v; }
    public String getLastErrorMessage() { return lastErrorMessage; } public void setLastErrorMessage(String v) { lastErrorMessage=v; }
    public String getXmlPath() { return xmlPath; } public void setXmlPath(String v) { xmlPath=v; }
    public String getSignedXmlPath() { return signedXmlPath; } public void setSignedXmlPath(String v) { signedXmlPath=v; }
    public String getPdfPath() { return pdfPath; } public void setPdfPath(String v) { pdfPath=v; }
    public String getCdrPath() { return cdrPath; } public void setCdrPath(String v) { cdrPath=v; }
    public String getXmlHash() { return xmlHash; } public void setXmlHash(String v) { xmlHash=v; }
    public String getCdrCode() { return cdrCode; } public void setCdrCode(String v) { cdrCode=v; }
    public String getCdrDescription() { return cdrDescription; } public void setCdrDescription(String v) { cdrDescription=v; }
    public Instant getAcceptedAt() { return acceptedAt; } public void setAcceptedAt(Instant v) { acceptedAt=v; }
    public UUID getDailySummaryId() { return dailySummaryId; } public void setDailySummaryId(UUID v) { dailySummaryId=v; }
    public DeliveryStatus getDeliveryStatus(){return deliveryStatus;} public void setDeliveryStatus(DeliveryStatus v){deliveryStatus=v;}
    public Instant getGrantedAt(){return grantedAt;} public void setGrantedAt(Instant v){grantedAt=v;}
    public DeliveryChannel getGrantedChannel(){return grantedChannel;} public void setGrantedChannel(DeliveryChannel v){grantedChannel=v;}
    public Instant getSubmissionUnknownSince(){return submissionUnknownSince;} public void setSubmissionUnknownSince(Instant v){submissionUnknownSince=v;}
    public int getReconciliationCount(){return reconciliationCount;} public void setReconciliationCount(int v){reconciliationCount=v;}
}
