package pe.com.perubilling.webhook.application;

import java.net.InetAddress;
import java.net.URI;
import org.springframework.stereotype.Component;
import pe.com.perubilling.shared.domain.BusinessException;

@Component
public class SafeWebhookUrlValidator {
    public void validate(String value) {
        try {
            URI uri = URI.create(value);
            if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null || uri.getUserInfo() != null)
                throw BusinessException.badRequest("UNSAFE_WEBHOOK_URL", "El webhook debe usar una URL HTTPS pública sin credenciales embebidas");
            for (InetAddress address : InetAddress.getAllByName(uri.getHost())) {
                if (address.isAnyLocalAddress() || address.isLoopbackAddress() || address.isLinkLocalAddress()
                        || address.isSiteLocalAddress() || address.isMulticastAddress()) {
                    throw BusinessException.badRequest("UNSAFE_WEBHOOK_URL", "El webhook no puede apuntar a redes locales o reservadas");
                }
            }
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            throw BusinessException.badRequest("INVALID_WEBHOOK_URL", "No se pudo validar la URL del webhook");
        }
    }
}
