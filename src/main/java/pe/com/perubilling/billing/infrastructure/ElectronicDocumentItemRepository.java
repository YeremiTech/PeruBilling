package pe.com.perubilling.billing.infrastructure;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import pe.com.perubilling.billing.domain.ElectronicDocumentItemEntity;

public interface ElectronicDocumentItemRepository extends JpaRepository<ElectronicDocumentItemEntity, UUID> {
    List<ElectronicDocumentItemEntity> findAllByTenantIdAndDocumentIdOrderByLineNumber(UUID tenantId, UUID documentId);
}
