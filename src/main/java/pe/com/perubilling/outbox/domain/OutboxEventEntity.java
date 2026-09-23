package pe.com.perubilling.outbox.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import pe.com.perubilling.shared.domain.BaseEntity;

@Entity
@Table(name = "outbox_event")
public class OutboxEventEntity extends BaseEntity {
    @Column(name="tenant_id", nullable=false) private UUID tenantId;
    @Column(name="event_type", nullable=false, length=100) private String eventType;
    @Column(name="aggregate_type", nullable=false, length=100) private String aggregateType;
    @Column(name="aggregate_id", nullable=false) private UUID aggregateId;
    @Column(nullable=false, columnDefinition="text") private String payload;
    @Column(nullable=false, length=20) private String status="PENDING";
    @Column(name="attempt_count", nullable=false) private int attemptCount;
    @Column(name="next_attempt_at") private Instant nextAttemptAt;
    @Column(name="processing_started_at") private Instant processingStartedAt;
    @Column(name="published_at") private Instant publishedAt;
    @Column(name="last_error", length=2000) private String lastError;

    public UUID getTenantId(){return tenantId;} public void setTenantId(UUID v){tenantId=v;}
    public String getEventType(){return eventType;} public void setEventType(String v){eventType=v;}
    public String getAggregateType(){return aggregateType;} public void setAggregateType(String v){aggregateType=v;}
    public UUID getAggregateId(){return aggregateId;} public void setAggregateId(UUID v){aggregateId=v;}
    public String getPayload(){return payload;} public void setPayload(String v){payload=v;}
    public String getStatus(){return status;} public void setStatus(String v){status=v;}
    public int getAttemptCount(){return attemptCount;} public void setAttemptCount(int v){attemptCount=v;}
    public Instant getNextAttemptAt(){return nextAttemptAt;} public void setNextAttemptAt(Instant v){nextAttemptAt=v;}
    public Instant getProcessingStartedAt(){return processingStartedAt;} public void setProcessingStartedAt(Instant v){processingStartedAt=v;}
    public Instant getPublishedAt(){return publishedAt;} public void setPublishedAt(Instant v){publishedAt=v;}
    public String getLastError(){return lastError;} public void setLastError(String v){lastError=v;}
}
