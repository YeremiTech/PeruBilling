package pe.com.perubilling.delivery.infrastructure;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import pe.com.perubilling.delivery.domain.DocumentAccessTokenEntity;

public interface DocumentAccessTokenRepository extends JpaRepository<DocumentAccessTokenEntity,UUID> {
    Optional<DocumentAccessTokenEntity> findByTokenHash(String tokenHash);
    List<DocumentAccessTokenEntity> findAllByTenantIdAndDocumentIdAndRevokedAtIsNull(UUID tenantId, UUID documentId);
}
