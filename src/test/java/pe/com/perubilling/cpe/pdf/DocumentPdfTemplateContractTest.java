package pe.com.perubilling.cpe.pdf;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class DocumentPdfTemplateContractTest {
    @Test
    void a4TemplateContainsProfessionalCpeSections() throws Exception {
        String html = template("/templates/pdf/cpe-a4.html");
        String fragment = template("/templates/pdf/fragments/cpe-a4-modern.html");
        assertThat(html).contains("fragments/cpe-a4-modern");
        assertThat(html).contains("counter(page)");
        assertThat(fragment).contains("Datos del cliente");
        assertThat(fragment).contains("Datos del comprobante");
        assertThat(fragment).contains("TOTAL A PAGAR");
        assertThat(fragment).contains("Escanee para verificar");
        assertThat(fragment).contains("QR SUNAT");
        assertThat(fragment).contains("DigestValue");
        assertThat(fragment).contains("Consulta y descarga");
        assertThat(fragment).contains("Documento afectado");
        assertThat(fragment).contains("brandLogoDataUri");
        assertThat(fragment).contains("watermarkLogoDataUri");
        assertThat(fragment).contains("sectionIcons.customer");
    }

    @Test
    void thermalTemplateContainsProfessionalTicketSections() throws Exception {
        String html = template("/templates/pdf/cpe-thermal-80.html");
        String fragment = template("/templates/pdf/fragments/cpe-thermal-modern.html");
        assertThat(html).contains("width: 80mm");
        assertThat(html).contains("fragments/cpe-thermal-modern");
        assertThat(fragment).contains("Cliente");
        assertThat(fragment).contains("Comprobante");
        assertThat(fragment).contains("Detalle");
        assertThat(fragment).contains("TOTAL");
        assertThat(fragment).contains("Escanee para verificar");
        assertThat(fragment).contains("QR SUNAT");
        assertThat(fragment).contains("DigestValue");
        assertThat(fragment).contains("Documento afectado");
        assertThat(fragment).contains("brandLogoDataUri");
        assertThat(fragment).contains("watermarkLogoDataUri");
        assertThat(fragment).contains("sectionIcons.validation");
    }

    private String template(String path) throws Exception {
        try (var in = getClass().getResourceAsStream(path)) {
            assertThat(in).isNotNull();
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
