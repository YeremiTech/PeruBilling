package pe.com.perubilling.summary.infrastructure;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import pe.com.perubilling.summary.domain.DailySummaryEntity;

public interface DailySummaryRepository extends JpaRepository<DailySummaryEntity, UUID> {
    Optional<DailySummaryEntity> findByIdAndTenantId(UUID id, UUID tenantId);
    Page<DailySummaryEntity> findAllByTenantIdOrderByCreatedAtDesc(UUID tenantId, Pageable pageable);
}
