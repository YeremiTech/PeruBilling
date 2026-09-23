package pe.com.perubilling.issuer.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import java.util.UUID;
import pe.com.perubilling.shared.domain.BaseEntity;

@Entity
@Table(
        name = "digital_certificate",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_certificate_issuer_fingerprint",
                columnNames = {"tenant_id", "issuer_id", "fingerprint"}))
public class DigitalCertificateEntity extends BaseEntity {
    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;
    @Column(name = "issuer_id", nullable = false)
    private UUID issuerId;
    @Column(name = "certificate_alias", length = 200)
    private String certificateAlias;
    @Column(name = "encrypted_pfx", nullable = false, columnDefinition = "bytea")
    private byte[] encryptedPfx;
    @Column(name = "password_encrypted", nullable = false, length = 1000)
    private String passwordEncrypted;
    @Column(nullable = false, length = 128)
    private String fingerprint;
    @Column(name = "subject_dn", nullable = false, length = 1000)
    private String subjectDn;
    @Column(name = "serial_number", nullable = false, length = 200)
    private String serialNumber;
    @Column(name = "valid_from", nullable = false)
    private Instant validFrom;
    @Column(name = "valid_until", nullable = false)
    private Instant validUntil;
    @Column(nullable = false)
    private boolean active = true;

    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }
    public UUID getIssuerId() { return issuerId; }
    public void setIssuerId(UUID issuerId) { this.issuerId = issuerId; }
    public String getCertificateAlias() { return certificateAlias; }
    public void setCertificateAlias(String certificateAlias) { this.certificateAlias = certificateAlias; }
    public byte[] getEncryptedPfx() { return encryptedPfx; }
    public void setEncryptedPfx(byte[] encryptedPfx) { this.encryptedPfx = encryptedPfx; }
    public String getPasswordEncrypted() { return passwordEncrypted; }
    public void setPasswordEncrypted(String passwordEncrypted) { this.passwordEncrypted = passwordEncrypted; }
    public String getFingerprint() { return fingerprint; }
    public void setFingerprint(String fingerprint) { this.fingerprint = fingerprint; }
    public String getSubjectDn() { return subjectDn; }
    public void setSubjectDn(String subjectDn) { this.subjectDn = subjectDn; }
    public String getSerialNumber() { return serialNumber; }
    public void setSerialNumber(String serialNumber) { this.serialNumber = serialNumber; }
    public Instant getValidFrom() { return validFrom; }
    public void setValidFrom(Instant validFrom) { this.validFrom = validFrom; }
    public Instant getValidUntil() { return validUntil; }
    public void setValidUntil(Instant validUntil) { this.validUntil = validUntil; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
}
