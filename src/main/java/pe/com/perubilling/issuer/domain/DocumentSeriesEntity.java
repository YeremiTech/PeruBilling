package pe.com.perubilling.issuer.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.util.UUID;
import pe.com.perubilling.shared.domain.DocumentType;
import pe.com.perubilling.shared.domain.BaseEntity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;

@Entity
@Table(name = "document_series")
public class DocumentSeriesEntity extends BaseEntity {
    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;
    @Column(name = "issuer_id", nullable = false)
    private UUID issuerId;
    @Enumerated(EnumType.STRING)
    @Column(name = "document_type", nullable = false, length = 30)
    private DocumentType documentType;
    @Column(nullable = false, length = 4)
    private String series;
    @Column(name = "current_value", nullable = false)
    private long currentValue;
    @Column(nullable = false)
    private boolean active = true;

    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }
    public UUID getIssuerId() { return issuerId; }
    public void setIssuerId(UUID issuerId) { this.issuerId = issuerId; }
    public DocumentType getDocumentType() { return documentType; }
    public void setDocumentType(DocumentType documentType) { this.documentType = documentType; }
    public String getSeries() { return series; }
    public void setSeries(String series) { this.series = series; }
    public long getCurrentValue() { return currentValue; }
    public void setCurrentValue(long currentValue) { this.currentValue = currentValue; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
}
