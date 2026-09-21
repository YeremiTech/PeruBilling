package pe.com.perubilling.billing.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import pe.com.perubilling.shared.domain.BaseEntity;

@Entity
@Table(name = "submission_attempt")
public class SubmissionAttemptEntity extends BaseEntity {
    @Column(name = "tenant_id", nullable = false) private UUID tenantId;
    @Column(name = "document_id", nullable = false) private UUID documentId;
    @Column(name = "attempt_number", nullable = false) private int attemptNumber;
    @Column(name = "started_at", nullable = false) private Instant startedAt;
    @Column(name = "finished_at") private Instant finishedAt;
    @Column(name = "success", nullable = false) private boolean success;
    @Column(name = "http_status") private Integer httpStatus;
    @Column(name = "response_code", length = 100) private String responseCode;
    @Column(name = "response_message", length = 2000) private String responseMessage;
    @Column(name = "duration_ms") private Long durationMs;
    public UUID getTenantId() { return tenantId; } public void setTenantId(UUID v) { tenantId=v; }
    public UUID getDocumentId() { return documentId; } public void setDocumentId(UUID v) { documentId=v; }
    public int getAttemptNumber() { return attemptNumber; } public void setAttemptNumber(int v) { attemptNumber=v; }
    public Instant getStartedAt() { return startedAt; } public void setStartedAt(Instant v) { startedAt=v; }
    public Instant getFinishedAt() { return finishedAt; } public void setFinishedAt(Instant v) { finishedAt=v; }
    public boolean isSuccess() { return success; } public void setSuccess(boolean v) { success=v; }
    public Integer getHttpStatus() { return httpStatus; } public void setHttpStatus(Integer v) { httpStatus=v; }
    public String getResponseCode() { return responseCode; } public void setResponseCode(String v) { responseCode=v; }
    public String getResponseMessage() { return responseMessage; } public void setResponseMessage(String v) { responseMessage=v; }
    public Long getDurationMs() { return durationMs; } public void setDurationMs(Long v) { durationMs=v; }
}
