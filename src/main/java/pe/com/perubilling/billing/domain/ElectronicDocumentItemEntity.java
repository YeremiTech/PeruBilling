package pe.com.perubilling.billing.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.util.UUID;
import pe.com.perubilling.shared.domain.BaseEntity;

@Entity
@Table(name = "electronic_document_item")
public class ElectronicDocumentItemEntity extends BaseEntity {
    @Column(name = "tenant_id", nullable = false) private UUID tenantId;
    @Column(name = "document_id", nullable = false) private UUID documentId;
    @Column(name = "line_number", nullable = false) private int lineNumber;
    @Column(length = 100) private String sku;
    @Column(name = "sunat_product_code", length = 8) private String sunatProductCode;
    @Column(name = "gtin", length = 14) private String gtin;
    @Column(name = "gtin_scheme_id", length = 14) private String gtinSchemeId;
    @Column(nullable = false, length = 500) private String description;
    @Column(name = "unit_code", nullable = false, length = 5) private String unitCode;
    @Column(nullable = false, precision = 18, scale = 6) private BigDecimal quantity;
    @Column(name = "unit_value", nullable = false, precision = 18, scale = 6) private BigDecimal unitValue;
    @Column(name = "unit_price", nullable = false, precision = 18, scale = 6) private BigDecimal unitPrice;
    @Column(name = "tax_affectation_code", nullable = false, length = 2) private String taxAffectationCode;
    @Column(name = "igv_rate", nullable = false, precision = 7, scale = 4) private BigDecimal igvRate;
    @Column(name = "line_gross_amount", nullable = false, precision = 18, scale = 2) private BigDecimal lineGrossAmount = BigDecimal.ZERO;
    @Column(name = "line_allowance_amount", nullable = false, precision = 18, scale = 2) private BigDecimal lineAllowanceAmount = BigDecimal.ZERO;
    @Column(name = "line_charge_amount", nullable = false, precision = 18, scale = 2) private BigDecimal lineChargeAmount = BigDecimal.ZERO;
    @Column(name = "line_base_amount", nullable = false, precision = 18, scale = 2) private BigDecimal lineBaseAmount;
    @Column(name = "line_igv_amount", nullable = false, precision = 18, scale = 2) private BigDecimal lineIgvAmount;
    @Column(name = "icbper_per_unit", nullable = false, precision = 18, scale = 4) private BigDecimal icbperPerUnit = BigDecimal.ZERO;
    @Column(name = "line_icbper_amount", nullable = false, precision = 18, scale = 2) private BigDecimal lineIcbperAmount = BigDecimal.ZERO;
    @Column(name = "line_total_amount", nullable = false, precision = 18, scale = 2) private BigDecimal lineTotalAmount;
    @Column(name = "free_operation", nullable = false) private boolean freeOperation;

    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID v) { tenantId = v; }
    public UUID getDocumentId() { return documentId; }
    public void setDocumentId(UUID v) { documentId = v; }
    public int getLineNumber() { return lineNumber; }
    public void setLineNumber(int v) { lineNumber = v; }
    public String getSku() { return sku; }
    public void setSku(String v) { sku = v; }
    public String getSunatProductCode() { return sunatProductCode; }
    public void setSunatProductCode(String v) { sunatProductCode = v; }
    public String getGtin() { return gtin; }
    public void setGtin(String v) { gtin = v; }
    public String getGtinSchemeId() { return gtinSchemeId; }
    public void setGtinSchemeId(String v) { gtinSchemeId = v; }
    public String getDescription() { return description; }
    public void setDescription(String v) { description = v; }
    public String getUnitCode() { return unitCode; }
    public void setUnitCode(String v) { unitCode = v; }
    public BigDecimal getQuantity() { return quantity; }
    public void setQuantity(BigDecimal v) { quantity = v; }
    public BigDecimal getUnitValue() { return unitValue; }
    public void setUnitValue(BigDecimal v) { unitValue = v; }
    public BigDecimal getUnitPrice() { return unitPrice; }
    public void setUnitPrice(BigDecimal v) { unitPrice = v; }
    public String getTaxAffectationCode() { return taxAffectationCode; }
    public void setTaxAffectationCode(String v) { taxAffectationCode = v; }
    public BigDecimal getIgvRate() { return igvRate; }
    public void setIgvRate(BigDecimal v) { igvRate = v; }
    public BigDecimal getLineGrossAmount() { return lineGrossAmount; }
    public void setLineGrossAmount(BigDecimal v) { lineGrossAmount = v; }
    public BigDecimal getLineAllowanceAmount() { return lineAllowanceAmount; }
    public void setLineAllowanceAmount(BigDecimal v) { lineAllowanceAmount = v; }
    public BigDecimal getLineChargeAmount() { return lineChargeAmount; }
    public void setLineChargeAmount(BigDecimal v) { lineChargeAmount = v; }
    public BigDecimal getLineBaseAmount() { return lineBaseAmount; }
    public void setLineBaseAmount(BigDecimal v) { lineBaseAmount = v; }
    public BigDecimal getLineIgvAmount() { return lineIgvAmount; }
    public void setLineIgvAmount(BigDecimal v) { lineIgvAmount = v; }
    public BigDecimal getIcbperPerUnit() { return icbperPerUnit; }
    public void setIcbperPerUnit(BigDecimal v) { icbperPerUnit = v; }
    public BigDecimal getLineIcbperAmount() { return lineIcbperAmount; }
    public void setLineIcbperAmount(BigDecimal v) { lineIcbperAmount = v; }
    public BigDecimal getLineTotalAmount() { return lineTotalAmount; }
    public void setLineTotalAmount(BigDecimal v) { lineTotalAmount = v; }
    public boolean isFreeOperation() { return freeOperation; }
    public void setFreeOperation(boolean v) { freeOperation = v; }
}
