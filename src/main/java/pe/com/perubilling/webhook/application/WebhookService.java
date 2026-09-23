package pe.com.perubilling.webhook.application;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.com.perubilling.outbox.application.OutboxPublisher;
import pe.com.perubilling.shared.crypto.SecretCryptoService;
import pe.com.perubilling.shared.domain.BusinessException;
import pe.com.perubilling.shared.security.TenantContext;
import pe.com.perubilling.webhook.api.CreateWebhookRequest;
import pe.com.perubilling.webhook.api.WebhookResponse;
import pe.com.perubilling.webhook.domain.WebhookEndpointEntity;
import pe.com.perubilling.webhook.infrastructure.WebhookEndpointRepository;

@Service
public class WebhookService {
    private static final Set<String> ALLOWED=Set.of(
            "document.accepted","document.observed","document.rejected","document.failed","document.voided");
    private final WebhookEndpointRepository endpoints;
    private final TenantContext tenant;
    private final SecretCryptoService crypto;
    private final SafeWebhookUrlValidator urlValidator;
    private final OutboxPublisher outbox;
    private final SecureRandom random=new SecureRandom();

    public WebhookService(WebhookEndpointRepository endpoints, TenantContext tenant, SecretCryptoService crypto,
                          SafeWebhookUrlValidator urlValidator, OutboxPublisher outbox) {
        this.endpoints=endpoints; this.tenant=tenant; this.crypto=crypto; this.urlValidator=urlValidator; this.outbox=outbox;
    }

    @Transactional
    public WebhookResponse create(CreateWebhookRequest request) {
        urlValidator.validate(request.url());
        var types=new TreeSet<>(request.eventTypes());
        if(!ALLOWED.containsAll(types)) throw BusinessException.badRequest("INVALID_WEBHOOK_EVENTS","Hay eventos webhook no soportados");
        byte[] bytes=new byte[32]; random.nextBytes(bytes);
        String secret="whsec_"+Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        var endpoint=new WebhookEndpointEntity();
        endpoint.setTenantId(tenant.requireTenantId());
        endpoint.setUrl(request.url().trim());
        endpoint.setEventTypes(String.join(",",types));
        endpoint.setSecretEncrypted(crypto.encrypt(secret));
        endpoints.save(endpoint);
        return map(endpoint,secret);
    }

    @Transactional(readOnly=true)
    public List<WebhookResponse> list() {
        return endpoints.findAllByTenantIdOrderByCreatedAtDesc(tenant.requireTenantId()).stream().map(e->map(e,null)).toList();
    }

    @Transactional
    public void disable(UUID id) {
        var endpoint=endpoints.findByIdAndTenantId(id,tenant.requireTenantId())
                .orElseThrow(()->BusinessException.notFound("WEBHOOK_NOT_FOUND","Webhook no encontrado"));
        endpoint.setActive(false);
    }

    @Transactional
    public void publish(UUID tenantId,String eventType,UUID resourceId,Map<String,Object> data) {
        if(!ALLOWED.contains(eventType)) throw new IllegalArgumentException("Evento webhook no soportado: "+eventType);
        outbox.publish(tenantId,eventType,"electronic_document",resourceId,data);
    }

    private WebhookResponse map(WebhookEndpointEntity e,String secret) {
        return new WebhookResponse(e.getId(),e.getUrl(),Set.of(e.getEventTypes().split(",")),e.isActive(),e.getCreatedAt(),secret);
    }
}
