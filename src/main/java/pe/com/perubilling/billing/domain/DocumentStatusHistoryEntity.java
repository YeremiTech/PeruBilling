package pe.com.perubilling.billing.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.util.UUID;
import pe.com.perubilling.shared.domain.BaseEntity;

@Entity
@Table(name = "document_status_history")
public class DocumentStatusHistoryEntity extends BaseEntity {
    @Column(name = "tenant_id", nullable = false) private UUID tenantId;
    @Column(name = "document_id", nullable = false) private UUID documentId;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 30) private DocumentStatus status;
    @Column(length = 100) private String code;
    @Column(length = 2000) private String message;
    public UUID getTenantId() { return tenantId; } public void setTenantId(UUID v) { tenantId=v; }
    public UUID getDocumentId() { return documentId; } public void setDocumentId(UUID v) { documentId=v; }
    public DocumentStatus getStatus() { return status; } public void setStatus(DocumentStatus v) { status=v; }
    public String getCode() { return code; } public void setCode(String v) { code=v; }
    public String getMessage() { return message; } public void setMessage(String v) { message=v; }
}
