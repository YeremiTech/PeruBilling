package pe.com.perubilling.webhook.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import pe.com.perubilling.shared.domain.BaseEntity;

@Entity
@Table(name = "webhook_delivery")
public class WebhookDeliveryEntity extends BaseEntity {
    @Column(name="tenant_id",nullable=false) private UUID tenantId;
    @Column(name="endpoint_id",nullable=false) private UUID endpointId;
    @Column(name="event_id",nullable=false,unique=true) private UUID eventId;
    @Column(name="source_event_id") private UUID sourceEventId;
    @Column(name="event_type",nullable=false,length=100) private String eventType;
    @Column(name="resource_id",nullable=false) private UUID resourceId;
    @Column(nullable=false,columnDefinition="text") private String payload;
    @Column(nullable=false,length=20) private String status="PENDING";
    @Column(name="attempt_count",nullable=false) private int attemptCount;
    @Column(name="next_attempt_at") private Instant nextAttemptAt;
    @Column(name="sending_started_at") private Instant sendingStartedAt;
    @Column(name="last_http_status") private Integer lastHttpStatus;
    @Column(name="last_error",length=2000) private String lastError;

    public UUID getTenantId(){return tenantId;} public void setTenantId(UUID v){tenantId=v;}
    public UUID getEndpointId(){return endpointId;} public void setEndpointId(UUID v){endpointId=v;}
    public UUID getEventId(){return eventId;} public void setEventId(UUID v){eventId=v;}
    public UUID getSourceEventId(){return sourceEventId;} public void setSourceEventId(UUID v){sourceEventId=v;}
    public String getEventType(){return eventType;} public void setEventType(String v){eventType=v;}
    public UUID getResourceId(){return resourceId;} public void setResourceId(UUID v){resourceId=v;}
    public String getPayload(){return payload;} public void setPayload(String v){payload=v;}
    public String getStatus(){return status;} public void setStatus(String v){status=v;}
    public int getAttemptCount(){return attemptCount;} public void setAttemptCount(int v){attemptCount=v;}
    public Instant getNextAttemptAt(){return nextAttemptAt;} public void setNextAttemptAt(Instant v){nextAttemptAt=v;}
    public Instant getSendingStartedAt(){return sendingStartedAt;} public void setSendingStartedAt(Instant v){sendingStartedAt=v;}
    public Integer getLastHttpStatus(){return lastHttpStatus;} public void setLastHttpStatus(Integer v){lastHttpStatus=v;}
    public String getLastError(){return lastError;} public void setLastError(String v){lastError=v;}
}
