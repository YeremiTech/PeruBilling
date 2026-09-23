package pe.com.perubilling.sunat.infrastructure;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import org.junit.jupiter.api.Test;
import pe.com.perubilling.issuer.domain.SunatEnvironment;

class SunatEndpointPolicyTest {
    private final SunatEndpointPolicy policy = new SunatEndpointPolicy();

    @Test
    void acceptsOfficialBetaHost() {
        String url = "https://e-beta.sunat.gob.pe/ol-ti-itcpfegem-beta/billService";
        assertEquals(url, policy.validate(SunatEnvironment.BETA, url));
    }

    @Test
    void rejectsArbitraryProductionHost() {
        assertThrows(IllegalStateException.class,
                () -> policy.validate(SunatEnvironment.PRODUCTION, "https://evil.example/billService"));
    }

    @Test
    void rejectsHttp() {
        assertThrows(IllegalStateException.class,
                () -> policy.validate(SunatEnvironment.BETA, "http://e-beta.sunat.gob.pe/test"));
    }
}
