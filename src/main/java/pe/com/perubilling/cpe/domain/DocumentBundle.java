package pe.com.perubilling.cpe.domain;

import java.util.List;
import pe.com.perubilling.billing.domain.AllowanceChargeEntity;
import pe.com.perubilling.billing.domain.DocumentAllowanceChargeEntity;
import pe.com.perubilling.billing.domain.ElectronicDocumentEntity;
import pe.com.perubilling.billing.domain.ElectronicDocumentItemEntity;
import pe.com.perubilling.billing.domain.PaymentInstallmentEntity;
import pe.com.perubilling.issuer.domain.IssuerEntity;

public record DocumentBundle(
        ElectronicDocumentEntity document,
        IssuerEntity issuer,
        List<ElectronicDocumentItemEntity> items,
        List<PaymentInstallmentEntity> installments,
        List<AllowanceChargeEntity> allowanceCharges,
        List<DocumentAllowanceChargeEntity> documentAllowanceCharges
) {
    public DocumentBundle(
            ElectronicDocumentEntity document,
            IssuerEntity issuer,
            List<ElectronicDocumentItemEntity> items,
            List<PaymentInstallmentEntity> installments) {
        this(document, issuer, items, installments, List.of(), List.of());
    }

    public DocumentBundle(
            ElectronicDocumentEntity document,
            IssuerEntity issuer,
            List<ElectronicDocumentItemEntity> items,
            List<PaymentInstallmentEntity> installments,
            List<AllowanceChargeEntity> allowanceCharges) {
        this(document, issuer, items, installments, allowanceCharges, List.of());
    }
}
