package pe.com.perubilling.billing.infrastructure;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import pe.com.perubilling.billing.domain.DocumentStatusHistoryEntity;

public interface DocumentStatusHistoryRepository extends JpaRepository<DocumentStatusHistoryEntity, UUID> {
    List<DocumentStatusHistoryEntity> findAllByTenantIdAndDocumentIdOrderByCreatedAtAsc(UUID tenantId, UUID documentId);
}
