package pe.com.perubilling.delivery.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import pe.com.perubilling.shared.domain.BaseEntity;

@Entity
@Table(name="document_access_token")
public class DocumentAccessTokenEntity extends BaseEntity {
    @Column(name="tenant_id",nullable=false) private UUID tenantId;
    @Column(name="document_id",nullable=false) private UUID documentId;
    @Column(name="token_hash",nullable=false,length=64,unique=true) private String tokenHash;
    @Column(name="expires_at",nullable=false) private Instant expiresAt;
    @Column(name="revoked_at") private Instant revokedAt;
    @Column(name="last_access_at") private Instant lastAccessAt;

    public UUID getTenantId(){return tenantId;} public void setTenantId(UUID v){tenantId=v;}
    public UUID getDocumentId(){return documentId;} public void setDocumentId(UUID v){documentId=v;}
    public String getTokenHash(){return tokenHash;} public void setTokenHash(String v){tokenHash=v;}
    public Instant getExpiresAt(){return expiresAt;} public void setExpiresAt(Instant v){expiresAt=v;}
    public Instant getRevokedAt(){return revokedAt;} public void setRevokedAt(Instant v){revokedAt=v;}
    public Instant getLastAccessAt(){return lastAccessAt;} public void setLastAccessAt(Instant v){lastAccessAt=v;}
}
