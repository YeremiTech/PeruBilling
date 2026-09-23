package pe.com.perubilling.sunat.infrastructure;

import java.net.URI;
import org.springframework.stereotype.Component;
import pe.com.perubilling.issuer.domain.SunatEnvironment;

@Component
public class SunatEndpointPolicy {
    public String validate(SunatEnvironment environment, String endpoint) {
        try {
            URI uri = URI.create(endpoint);
            if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getUserInfo() != null || uri.getHost() == null) {
                throw new IllegalStateException("El endpoint SUNAT debe ser HTTPS, sin user-info y con host explícito");
            }
            String expected = switch (environment) {
                case BETA -> "e-beta.sunat.gob.pe";
                case PRODUCTION -> "e-factura.sunat.gob.pe";
                case LOCAL -> throw new IllegalArgumentException("LOCAL no requiere endpoint remoto");
            };
            if (!expected.equalsIgnoreCase(uri.getHost())) {
                throw new IllegalStateException("Host SUNAT no autorizado para " + environment + ": " + uri.getHost());
            }
            if (uri.getPort() != -1 && uri.getPort() != 443) {
                throw new IllegalStateException("Solo se permite HTTPS por puerto 443 para SUNAT");
            }
            return uri.toString();
        } catch (IllegalArgumentException ex) {
            throw new IllegalStateException("Endpoint SUNAT inválido", ex);
        }
    }
}
