package pe.com.perubilling.delivery.api;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

public record CreateAccessLinkRequest(@Min(365) @Max(730) Integer validDays) {}
