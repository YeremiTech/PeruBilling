package pe.com.perubilling.billing.infrastructure;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import pe.com.perubilling.billing.domain.DocumentStatus;
import pe.com.perubilling.billing.domain.DocumentStatusHistoryEntity;

public interface DocumentStatusHistoryRepository extends JpaRepository<DocumentStatusHistoryEntity, UUID> {
    List<DocumentStatusHistoryEntity> findAllByTenantIdAndDocumentIdOrderByCreatedAtAsc(UUID tenantId, UUID documentId);

    Optional<DocumentStatusHistoryEntity> findFirstByTenantIdAndDocumentIdAndStatusInOrderByCreatedAtDesc(
            UUID tenantId, UUID documentId, Collection<DocumentStatus> statuses);
}
