package pe.com.perubilling.shared.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class RequestContextFilterTest {
    private final RequestContextFilter filter = new RequestContextFilter();

    @Test
    void preservesValidCallerRequestIdAndExposesApiVersion() throws Exception {
        var request = new MockHttpServletRequest();
        request.addHeader("X-Request-Id", "erp-order-42");
        var response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertEquals("erp-order-42", response.getHeader("X-Request-Id"));
        assertEquals("v1", response.getHeader("X-API-Version"));
    }

    @Test
    void replacesOversizedRequestId() throws Exception {
        var request = new MockHttpServletRequest();
        request.addHeader("X-Request-Id", "x".repeat(101));
        var response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertNotNull(response.getHeader("X-Request-Id"));
        assertEquals(36, response.getHeader("X-Request-Id").length());
    }
}
