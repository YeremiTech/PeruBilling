package pe.com.perubilling.billing.infrastructure;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import pe.com.perubilling.billing.domain.AllowanceChargeEntity;

public interface AllowanceChargeRepository extends JpaRepository<AllowanceChargeEntity, UUID> {
    List<AllowanceChargeEntity> findAllByTenantIdAndDocumentIdOrderByLineNumberAscSequenceNumberAsc(
            UUID tenantId, UUID documentId);
}
