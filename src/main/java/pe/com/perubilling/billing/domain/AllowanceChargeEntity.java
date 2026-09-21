package pe.com.perubilling.billing.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.util.UUID;
import pe.com.perubilling.shared.domain.BaseEntity;

@Entity
@Table(name = "document_item_allowance_charge")
public class AllowanceChargeEntity extends BaseEntity {
    @Column(name = "tenant_id", nullable = false) private UUID tenantId;
    @Column(name = "document_id", nullable = false) private UUID documentId;
    @Column(name = "document_item_id", nullable = false) private UUID documentItemId;
    @Column(name = "line_number", nullable = false) private int lineNumber;
    @Column(name = "sequence_number", nullable = false) private int sequenceNumber;
    @Column(name = "charge_indicator", nullable = false) private boolean charge;
    @Column(name = "reason_code", nullable = false, length = 2) private String reasonCode;
    @Column(nullable = false, precision = 9, scale = 6) private BigDecimal factor;
    @Column(nullable = false, precision = 18, scale = 2) private BigDecimal amount;
    @Column(name = "base_amount", nullable = false, precision = 18, scale = 2) private BigDecimal baseAmount;

    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }
    public UUID getDocumentId() { return documentId; }
    public void setDocumentId(UUID documentId) { this.documentId = documentId; }
    public UUID getDocumentItemId() { return documentItemId; }
    public void setDocumentItemId(UUID documentItemId) { this.documentItemId = documentItemId; }
    public int getLineNumber() { return lineNumber; }
    public void setLineNumber(int lineNumber) { this.lineNumber = lineNumber; }
    public int getSequenceNumber() { return sequenceNumber; }
    public void setSequenceNumber(int sequenceNumber) { this.sequenceNumber = sequenceNumber; }
    public boolean isCharge() { return charge; }
    public void setCharge(boolean charge) { this.charge = charge; }
    public String getReasonCode() { return reasonCode; }
    public void setReasonCode(String reasonCode) { this.reasonCode = reasonCode; }
    public BigDecimal getFactor() { return factor; }
    public void setFactor(BigDecimal factor) { this.factor = factor; }
    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }
    public BigDecimal getBaseAmount() { return baseAmount; }
    public void setBaseAmount(BigDecimal baseAmount) { this.baseAmount = baseAmount; }
}
