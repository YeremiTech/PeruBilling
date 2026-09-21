package pe.com.perubilling.issuer.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.util.UUID;
import pe.com.perubilling.shared.domain.BaseEntity;

@Entity
@Table(name = "issuer")
public class IssuerEntity extends BaseEntity {
    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;
    @Column(nullable = false, length = 11)
    private String ruc;
    @Column(name = "business_name", nullable = false, length = 250)
    private String businessName;
    @Column(name = "trade_name", length = 250)
    private String tradeName;
    @Column(nullable = false, length = 250)
    private String address;
    @Column(nullable = false, length = 6)
    private String ubigeo;
    @Column(name = "establishment_code", nullable = false, length = 4)
    private String establishmentCode = "0000";
    @Column(name = "department", length = 100)
    private String department;
    @Column(name = "province", length = 100)
    private String province;
    @Column(name = "district", length = 100)
    private String district;
    @Enumerated(EnumType.STRING)
    @Column(name = "sunat_environment", nullable = false, length = 20)
    private SunatEnvironment sunatEnvironment = SunatEnvironment.LOCAL;
    @Column(name = "sol_user", length = 100)
    private String solUser;
    @Column(name = "sol_password_encrypted", length = 1000)
    private String solPasswordEncrypted;
    @Column(nullable = false)
    private boolean active = true;

    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }
    public String getRuc() { return ruc; }
    public void setRuc(String ruc) { this.ruc = ruc; }
    public String getBusinessName() { return businessName; }
    public void setBusinessName(String businessName) { this.businessName = businessName; }
    public String getTradeName() { return tradeName; }
    public void setTradeName(String tradeName) { this.tradeName = tradeName; }
    public String getAddress() { return address; }
    public void setAddress(String address) { this.address = address; }
    public String getUbigeo() { return ubigeo; }
    public void setUbigeo(String ubigeo) { this.ubigeo = ubigeo; }
    public String getEstablishmentCode() { return establishmentCode; }
    public void setEstablishmentCode(String establishmentCode) { this.establishmentCode = establishmentCode; }
    public String getDepartment() { return department; }
    public void setDepartment(String department) { this.department = department; }
    public String getProvince() { return province; }
    public void setProvince(String province) { this.province = province; }
    public String getDistrict() { return district; }
    public void setDistrict(String district) { this.district = district; }
    public SunatEnvironment getSunatEnvironment() { return sunatEnvironment; }
    public void setSunatEnvironment(SunatEnvironment sunatEnvironment) { this.sunatEnvironment = sunatEnvironment; }
    public String getSolUser() { return solUser; }
    public void setSolUser(String solUser) { this.solUser = solUser; }
    public String getSolPasswordEncrypted() { return solPasswordEncrypted; }
    public void setSolPasswordEncrypted(String solPasswordEncrypted) { this.solPasswordEncrypted = solPasswordEncrypted; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
}
