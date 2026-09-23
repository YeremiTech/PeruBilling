package pe.com.perubilling.issuer.infrastructure;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import pe.com.perubilling.issuer.domain.IssuerEntity;

public interface IssuerRepository extends JpaRepository<IssuerEntity, UUID> {
    List<IssuerEntity> findAllByTenantIdOrderByBusinessName(UUID tenantId);
    Optional<IssuerEntity> findByIdAndTenantId(UUID id, UUID tenantId);
    boolean existsByTenantIdAndRuc(UUID tenantId, String ruc);
}
