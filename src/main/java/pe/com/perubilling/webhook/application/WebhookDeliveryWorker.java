package pe.com.perubilling.webhook.application;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import pe.com.perubilling.shared.crypto.SecretCryptoService;
import pe.com.perubilling.webhook.domain.WebhookDeliveryEntity;
import pe.com.perubilling.webhook.infrastructure.WebhookDeliveryRepository;
import pe.com.perubilling.webhook.infrastructure.WebhookEndpointRepository;
import pe.com.perubilling.webhook.infrastructure.WebhookQueueRepository;

@Component
public class WebhookDeliveryWorker {
    private static final int BATCH_SIZE = 20;

    private final WebhookQueueRepository queue;
    private final WebhookDeliveryRepository deliveries;
    private final WebhookEndpointRepository endpoints;
    private final SecretCryptoService crypto;
    private final SafeWebhookUrlValidator urlValidator;
    private final HttpClient client;
    private final int maxAttempts;
    private final Duration requestTimeout;

    public WebhookDeliveryWorker(
            WebhookQueueRepository queue,
            WebhookDeliveryRepository deliveries,
            WebhookEndpointRepository endpoints,
            SecretCryptoService crypto,
            SafeWebhookUrlValidator urlValidator,
            @Value("${app.webhooks.max-attempts:8}") int maxAttempts,
            @Value("${app.webhooks.connect-timeout-seconds:5}") long connectTimeoutSeconds,
            @Value("${app.webhooks.request-timeout-seconds:10}") long requestTimeoutSeconds) {
        this.queue = queue;
        this.deliveries = deliveries;
        this.endpoints = endpoints;
        this.crypto = crypto;
        this.urlValidator = urlValidator;
        this.maxAttempts = maxAttempts;
        this.requestTimeout = Duration.ofSeconds(requestTimeoutSeconds);
        this.client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(connectTimeoutSeconds))
                .build();
    }

    @Scheduled(fixedDelayString = "${app.webhooks.poll-ms:3000}")
    public void run() {
        queue.recoverStaleSending(5);
        for (UUID deliveryId : queue.claim(BATCH_SIZE)) {
            send(deliveryId);
        }
    }

    private void send(UUID deliveryId) {
        var delivery = deliveries.findById(deliveryId).orElse(null);
        if (delivery == null) {
            return;
        }
        var endpoint = endpoints.findById(delivery.getEndpointId()).orElse(null);
        if (endpoint == null || !endpoint.isActive()) {
            delivery.setStatus("FAILED");
            delivery.setLastError("Webhook deshabilitado o inexistente");
            deliveries.save(delivery);
            return;
        }

        int attempt = delivery.getAttemptCount() + 1;
        long timestamp = Instant.now().getEpochSecond();
        try {
            urlValidator.validate(endpoint.getUrl());
            String secret = crypto.decrypt(endpoint.getSecretEncrypted());
            String signature = sign(secret, timestamp + "." + delivery.getPayload());
            var request = HttpRequest.newBuilder(URI.create(endpoint.getUrl()))
                    .timeout(requestTimeout)
                    .header("Content-Type", "application/json")
                    .header("User-Agent", "PeruBilling-Webhooks/1.0")
                    .header("X-PeruBilling-Event", delivery.getEventType())
                    .header("X-PeruBilling-Event-Id", delivery.getEventId().toString())
                    .header("X-PeruBilling-Timestamp", String.valueOf(timestamp))
                    .header("X-PeruBilling-Signature", "v1=" + signature)
                    .POST(HttpRequest.BodyPublishers.ofString(delivery.getPayload(), StandardCharsets.UTF_8))
                    .build();
            var response = client.send(request, HttpResponse.BodyHandlers.discarding());
            delivery.setAttemptCount(attempt);
            delivery.setLastHttpStatus(response.statusCode());
            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                delivery.setStatus("DELIVERED");
                delivery.setLastError(null);
                delivery.setNextAttemptAt(null);
                delivery.setSendingStartedAt(null);
            } else {
                retry(delivery, attempt, "HTTP " + response.statusCode());
            }
        } catch (Exception ex) {
            if (ex instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            delivery.setAttemptCount(attempt);
            retry(delivery, attempt, safeMessage(ex));
        }
        deliveries.save(delivery);
    }

    private void retry(WebhookDeliveryEntity delivery, int attempt, String error) {
        delivery.setSendingStartedAt(null);
        delivery.setLastError(error.substring(0, Math.min(1900, error.length())));
        if (attempt >= maxAttempts) {
            delivery.setStatus("FAILED");
            delivery.setNextAttemptAt(null);
            return;
        }
        delivery.setStatus("RETRY");
        long delaySeconds = Math.min(3600, 15L * (1L << Math.min(attempt, 8)));
        delivery.setNextAttemptAt(Instant.now().plusSeconds(delaySeconds));
    }

    private String safeMessage(Exception ex) {
        return ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage();
    }

    private String sign(String secret, String value) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return HexFormat.of().formatHex(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));
    }
}
