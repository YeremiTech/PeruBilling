package pe.com.perubilling.issuer.api;

import java.util.UUID;
import pe.com.perubilling.issuer.domain.SunatEnvironment;

public record IssuerResponse(UUID id, String ruc, String businessName, String tradeName, String address, String ubigeo,
                             String establishmentCode, String department, String province, String district, SunatEnvironment environment, boolean active,
                             boolean sunatCredentialsConfigured) {}
