package pe.com.perubilling.billing.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.util.UUID;
import pe.com.perubilling.shared.domain.BaseEntity;

@Entity
@Table(name = "idempotency_record")
public class IdempotencyRecordEntity extends BaseEntity {
    @Column(name = "tenant_id", nullable = false) private UUID tenantId;
    @Column(name = "idempotency_key", nullable = false, length = 150) private String idempotencyKey;
    @Column(name = "request_hash", nullable = false, length = 64) private String requestHash;
    @Column(name = "resource_id", nullable = false) private UUID resourceId;
    public UUID getTenantId() { return tenantId; } public void setTenantId(UUID v) { tenantId=v; }
    public String getIdempotencyKey() { return idempotencyKey; } public void setIdempotencyKey(String v) { idempotencyKey=v; }
    public String getRequestHash() { return requestHash; } public void setRequestHash(String v) { requestHash=v; }
    public UUID getResourceId() { return resourceId; } public void setResourceId(UUID v) { resourceId=v; }
}
