package pe.com.perubilling.webhook.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;
import pe.com.perubilling.shared.domain.BusinessException;

class SafeWebhookUrlValidatorTest {
    private final SafeWebhookUrlValidator validator = new SafeWebhookUrlValidator();

    @Test
    void rejectsPlainHttp() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> validator.validate("http://example.com/hook"));
        assertEquals("UNSAFE_WEBHOOK_URL", ex.getCode());
    }

    @Test
    void rejectsLoopbackIpv4() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> validator.validate("https://127.0.0.1/hook"));
        assertEquals("UNSAFE_WEBHOOK_URL", ex.getCode());
    }

    @Test
    void rejectsEmbeddedCredentials() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> validator.validate("https://user:password@example.com/hook"));
        assertEquals("UNSAFE_WEBHOOK_URL", ex.getCode());
    }
}
