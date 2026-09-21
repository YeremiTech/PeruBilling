package pe.com.perubilling.issuer.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import pe.com.perubilling.issuer.domain.SunatEnvironment;

public record SunatCredentialsRequest(@NotBlank @Size(max = 100) String user, @NotBlank String password,
                                      @NotNull SunatEnvironment environment) {}
