package pe.com.perubilling.voiding.infrastructure;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import pe.com.perubilling.voiding.domain.VoidingBatchEntity;

public interface VoidingBatchRepository extends JpaRepository<VoidingBatchEntity, UUID> {
    Optional<VoidingBatchEntity> findByTenantIdAndDocumentId(UUID tenantId, UUID documentId);
    long countByTenantIdAndIssuerIdAndGenerationDate(UUID tenantId, UUID issuerId, LocalDate generationDate);
}
