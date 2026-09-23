package pe.com.perubilling.billing.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.RequestParam;
import pe.com.perubilling.shared.domain.DocumentPdfLayout;

class DocumentArtifactLayoutContractTest {
    @Test
    void pdfEndpointAcceptsLayoutQueryParameterWithA4Default() throws Exception {
        Method method = DocumentArtifactController.class.getDeclaredMethod(
                "pdf", java.util.UUID.class, DocumentPdfLayout.class);
        RequestParam parameter = method.getParameters()[1].getAnnotation(RequestParam.class);
        assertThat(parameter).isNotNull();
        assertThat(parameter.defaultValue()).isEqualTo("A4");
    }
}
