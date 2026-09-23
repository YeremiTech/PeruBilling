package pe.com.perubilling.delivery.api;

import jakarta.validation.constraints.NotNull;
import pe.com.perubilling.billing.domain.DeliveryChannel;

public record MarkGrantedRequest(@NotNull DeliveryChannel channel) {}
