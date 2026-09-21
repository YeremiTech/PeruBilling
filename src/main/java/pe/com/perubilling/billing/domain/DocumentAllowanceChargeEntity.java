package pe.com.perubilling.billing.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.util.UUID;
import pe.com.perubilling.shared.domain.BaseEntity;

@Entity
@Table(name = "document_allowance_charge")
public class DocumentAllowanceChargeEntity extends BaseEntity {
    @Column(name = "tenant_id", nullable = false) private UUID tenantId;
    @Column(name = "document_id", nullable = false) private UUID documentId;
    @Column(name = "sequence_number", nullable = false) private int sequenceNumber;
    @Column(name = "charge_indicator", nullable = false) private boolean charge;
    @Column(name = "reason_code", nullable = false, length = 2) private String reasonCode;
    @Column(nullable = false, precision = 9, scale = 6) private BigDecimal factor;
    @Column(nullable = false, precision = 18, scale = 2) private BigDecimal amount;
    @Column(name = "base_amount", nullable = false, precision = 18, scale = 2) private BigDecimal baseAmount;

    public UUID getTenantId() { return tenantId; } public void setTenantId(UUID v) { tenantId = v; }
    public UUID getDocumentId() { return documentId; } public void setDocumentId(UUID v) { documentId = v; }
    public int getSequenceNumber() { return sequenceNumber; } public void setSequenceNumber(int v) { sequenceNumber = v; }
    public boolean isCharge() { return charge; } public void setCharge(boolean v) { charge = v; }
    public String getReasonCode() { return reasonCode; } public void setReasonCode(String v) { reasonCode = v; }
    public BigDecimal getFactor() { return factor; } public void setFactor(BigDecimal v) { factor = v; }
    public BigDecimal getAmount() { return amount; } public void setAmount(BigDecimal v) { amount = v; }
    public BigDecimal getBaseAmount() { return baseAmount; } public void setBaseAmount(BigDecimal v) { baseAmount = v; }
}
