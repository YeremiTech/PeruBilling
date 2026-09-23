package pe.com.perubilling.billing.infrastructure;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import pe.com.perubilling.billing.domain.SubmissionAttemptEntity;

public interface SubmissionAttemptRepository extends JpaRepository<SubmissionAttemptEntity, UUID> {
    List<SubmissionAttemptEntity> findAllByTenantIdAndDocumentIdOrderByAttemptNumberAsc(UUID tenantId, UUID documentId);
}
