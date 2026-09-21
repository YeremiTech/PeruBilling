package pe.com.perubilling.webhook.infrastructure;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import pe.com.perubilling.webhook.domain.WebhookDeliveryEntity;

public interface WebhookDeliveryRepository extends JpaRepository<WebhookDeliveryEntity,UUID>{
    boolean existsBySourceEventIdAndEndpointId(UUID sourceEventId, UUID endpointId);
}
