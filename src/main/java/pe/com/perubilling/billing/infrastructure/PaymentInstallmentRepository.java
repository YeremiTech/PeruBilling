package pe.com.perubilling.billing.infrastructure;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import pe.com.perubilling.billing.domain.PaymentInstallmentEntity;

public interface PaymentInstallmentRepository extends JpaRepository<PaymentInstallmentEntity, UUID> {
    List<PaymentInstallmentEntity> findAllByTenantIdAndDocumentIdOrderByInstallmentNumber(UUID tenantId, UUID documentId);
}
