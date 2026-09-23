package pe.com.perubilling.webhook.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.util.UUID;
import pe.com.perubilling.shared.domain.BaseEntity;

@Entity
@Table(name = "webhook_endpoint")
public class WebhookEndpointEntity extends BaseEntity {
    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(nullable = false, length = 500)
    private String url;

    @Column(name = "secret_encrypted", nullable = false, length = 1000)
    private String secretEncrypted;

    @Column(name = "event_types", nullable = false, length = 1000)
    private String eventTypes;

    @Column(nullable = false)
    private boolean active = true;

    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }
    public String getUrl() { return url; }
    public void setUrl(String url) { this.url = url; }
    public String getSecretEncrypted() { return secretEncrypted; }
    public void setSecretEncrypted(String secretEncrypted) { this.secretEncrypted = secretEncrypted; }
    public String getEventTypes() { return eventTypes; }
    public void setEventTypes(String eventTypes) { this.eventTypes = eventTypes; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
}
