package pe.com.perubilling.summary.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import pe.com.perubilling.shared.domain.BaseEntity;

@Entity
@Table(name = "daily_summary")
public class DailySummaryEntity extends BaseEntity {
    @Column(name="tenant_id", nullable=false) private UUID tenantId;
    @Column(name="issuer_id", nullable=false) private UUID issuerId;
    @Column(name="reference_date", nullable=false) private LocalDate referenceDate;
    @Column(name="sequence_number", nullable=false) private long sequenceNumber;
    @Column(nullable=false, length=40) private String identifier;
    @Enumerated(EnumType.STRING) @Column(nullable=false, length=30) private DailySummaryStatus status;
    @Column(length=200) private String ticket;
    @Column(name="attempt_count", nullable=false) private int attemptCount;
    @Column(name="next_retry_at") private Instant nextRetryAt;
    @Column(name="xml_path", length=1000) private String xmlPath;
    @Column(name="signed_xml_path", length=1000) private String signedXmlPath;
    @Column(name="cdr_path", length=1000) private String cdrPath;
    @Column(name="response_code", length=50) private String responseCode;
    @Column(name="response_message", length=2000) private String responseMessage;

    public UUID getTenantId(){return tenantId;} public void setTenantId(UUID v){tenantId=v;}
    public UUID getIssuerId(){return issuerId;} public void setIssuerId(UUID v){issuerId=v;}
    public LocalDate getReferenceDate(){return referenceDate;} public void setReferenceDate(LocalDate v){referenceDate=v;}
    public long getSequenceNumber(){return sequenceNumber;} public void setSequenceNumber(long v){sequenceNumber=v;}
    public String getIdentifier(){return identifier;} public void setIdentifier(String v){identifier=v;}
    public DailySummaryStatus getStatus(){return status;} public void setStatus(DailySummaryStatus v){status=v;}
    public String getTicket(){return ticket;} public void setTicket(String v){ticket=v;}
    public int getAttemptCount(){return attemptCount;} public void setAttemptCount(int v){attemptCount=v;}
    public Instant getNextRetryAt(){return nextRetryAt;} public void setNextRetryAt(Instant v){nextRetryAt=v;}
    public String getXmlPath(){return xmlPath;} public void setXmlPath(String v){xmlPath=v;}
    public String getSignedXmlPath(){return signedXmlPath;} public void setSignedXmlPath(String v){signedXmlPath=v;}
    public String getCdrPath(){return cdrPath;} public void setCdrPath(String v){cdrPath=v;}
    public String getResponseCode(){return responseCode;} public void setResponseCode(String v){responseCode=v;}
    public String getResponseMessage(){return responseMessage;} public void setResponseMessage(String v){responseMessage=v;}
}
