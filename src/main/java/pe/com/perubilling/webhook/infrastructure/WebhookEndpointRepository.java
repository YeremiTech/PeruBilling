package pe.com.perubilling.webhook.infrastructure;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import pe.com.perubilling.webhook.domain.WebhookEndpointEntity;

public interface WebhookEndpointRepository extends JpaRepository<WebhookEndpointEntity, UUID> {
    List<WebhookEndpointEntity> findAllByTenantIdOrderByCreatedAtDesc(UUID tenantId);
    List<WebhookEndpointEntity> findAllByTenantIdAndActiveTrue(UUID tenantId);
    Optional<WebhookEndpointEntity> findByIdAndTenantId(UUID id, UUID tenantId);
}
