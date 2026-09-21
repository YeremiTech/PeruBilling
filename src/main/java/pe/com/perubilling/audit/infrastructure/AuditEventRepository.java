package pe.com.perubilling.audit.infrastructure;

import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import pe.com.perubilling.audit.domain.AuditEventEntity;

public interface AuditEventRepository extends JpaRepository<AuditEventEntity, UUID> {
    Page<AuditEventEntity> findAllByTenantIdOrderByCreatedAtDesc(UUID tenantId, Pageable pageable);
}
