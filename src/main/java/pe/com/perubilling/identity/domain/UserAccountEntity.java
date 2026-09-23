package pe.com.perubilling.identity.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import pe.com.perubilling.shared.domain.BaseEntity;

@Entity
@Table(name = "user_account")
public class UserAccountEntity extends BaseEntity {
    @Column(name = "tenant_id", nullable = false) private UUID tenantId;
    @Column(nullable = false, unique = true, length = 255) private String email;
    @Column(name = "password_hash", nullable = false, length = 100) private String passwordHash;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20) private UserRole role;
    @Column(nullable = false) private boolean enabled = true;
    @Column(name = "last_login_at") private Instant lastLoginAt;
    @Column(name = "failed_login_count", nullable = false) private int failedLoginCount;
    @Column(name = "locked_until") private Instant lockedUntil;

    public UUID getTenantId() { return tenantId; } public void setTenantId(UUID v) { tenantId=v; }
    public String getEmail() { return email; } public void setEmail(String v) { email=v; }
    public String getPasswordHash() { return passwordHash; } public void setPasswordHash(String v) { passwordHash=v; }
    public UserRole getRole() { return role; } public void setRole(UserRole v) { role=v; }
    public boolean isEnabled() { return enabled; } public void setEnabled(boolean v) { enabled=v; }
    public Instant getLastLoginAt() { return lastLoginAt; } public void setLastLoginAt(Instant v) { lastLoginAt=v; }
    public int getFailedLoginCount() { return failedLoginCount; } public void setFailedLoginCount(int v) { failedLoginCount=v; }
    public Instant getLockedUntil() { return lockedUntil; } public void setLockedUntil(Instant v) { lockedUntil=v; }
}
