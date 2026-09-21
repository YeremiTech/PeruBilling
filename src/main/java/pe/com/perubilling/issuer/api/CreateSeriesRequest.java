package pe.com.perubilling.issuer.api;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import pe.com.perubilling.shared.domain.DocumentType;

public record CreateSeriesRequest(@NotNull DocumentType documentType,
                                  @NotBlank @Pattern(regexp = "[FB][A-Z0-9]{3}") String series,
                                  @Min(1) long startAt) {}
