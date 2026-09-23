package pe.com.perubilling.tenant.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import pe.com.perubilling.shared.domain.BaseEntity;

@Entity
@Table(name = "tenant")
public class TenantEntity extends BaseEntity {
    @Column(nullable = false, length = 150)
    private String name;
    @Column(nullable = false, unique = true, length = 100)
    private String slug;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TenantStatus status = TenantStatus.ACTIVE;

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getSlug() { return slug; }
    public void setSlug(String slug) { this.slug = slug; }
    public TenantStatus getStatus() { return status; }
    public void setStatus(TenantStatus status) { this.status = status; }
}
