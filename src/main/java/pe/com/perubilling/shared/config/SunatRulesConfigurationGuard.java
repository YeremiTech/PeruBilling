package pe.com.perubilling.shared.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import pe.com.perubilling.shared.sunat.SunatRulesBaseline;

/** Impide anunciar una versión regulatoria distinta de la compilada y probada. */
@Component
public class SunatRulesConfigurationGuard {
    public SunatRulesConfigurationGuard(
            @Value("${app.sunat.rules.version:" + SunatRulesBaseline.VERSION + "}") String configuredVersion) {
        if (!SunatRulesBaseline.VERSION.equals(configuredVersion)) {
            throw new IllegalStateException(
                    "SUNAT_RULES_VERSION=" + configuredVersion + " no coincide con el baseline compilado "
                            + SunatRulesBaseline.VERSION
                            + ". Actualice código, fixtures y pruebas antes de cambiar la versión.");
        }
    }
}
