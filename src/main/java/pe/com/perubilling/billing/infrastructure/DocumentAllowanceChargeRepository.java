package pe.com.perubilling.billing.infrastructure;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import pe.com.perubilling.billing.domain.DocumentAllowanceChargeEntity;

public interface DocumentAllowanceChargeRepository extends JpaRepository<DocumentAllowanceChargeEntity, UUID> {
    List<DocumentAllowanceChargeEntity> findAllByTenantIdAndDocumentIdOrderBySequenceNumberAsc(UUID tenantId, UUID documentId);
}
