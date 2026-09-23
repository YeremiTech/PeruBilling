package pe.com.perubilling.webhook.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import java.util.Set;

public record CreateWebhookRequest(
        @NotBlank
        @Pattern(regexp = "https://.*", message = "El webhook debe usar HTTPS")
        String url,
        @NotEmpty Set<String> eventTypes) {}
