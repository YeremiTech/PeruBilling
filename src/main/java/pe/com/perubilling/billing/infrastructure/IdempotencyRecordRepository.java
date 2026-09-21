package pe.com.perubilling.billing.infrastructure;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import pe.com.perubilling.billing.domain.IdempotencyRecordEntity;

public interface IdempotencyRecordRepository extends JpaRepository<IdempotencyRecordEntity, UUID> {
    Optional<IdempotencyRecordEntity> findByTenantIdAndIdempotencyKey(UUID tenantId, String idempotencyKey);
}
