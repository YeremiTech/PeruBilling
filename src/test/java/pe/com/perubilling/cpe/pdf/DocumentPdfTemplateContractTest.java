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
        assertThat(html).contains("@bottom-center", "content: element(a4-footer)");
        assertThat(html).contains("width: 210mm");
        assertThat(html).contains("position: running(a4-footer)", "counter(page)", "counter(pages)");
        assertThat(html).doesNotContain(".sheet {", "border-radius: 5mm");
        assertThat(fragment).contains("Datos del cliente");
        assertThat(fragment).contains("Datos del comprobante");
        assertThat(fragment).contains("TOTAL A PAGAR");
        assertThat(fragment).contains("Escanee para verificar");
        assertThat(fragment).contains("QR SUNAT");
        assertThat(fragment).contains("DigestValue");
        assertThat(fragment).contains("Consulta y descarga");
        assertThat(fragment).contains("Documento afectado");
        assertThat(fragment).contains("brandLogoDataUri");
        assertThat(fragment).contains("sectionIcons.customer");
        assertThat(fragment).contains("brandAssets.hero");
        assertThat(fragment).contains("brandAssets.thanks");
        assertThat(fragment).contains("metaIcons.ruc");
        assertThat(fragment).contains("metaIcons.address", "metaIcons.ubigeo");
        assertThat(fragment).contains("footerIcons.shield", "footerIcons.globe");
        assertThat(fragment).contains("a4-footer-bar", "a4-footer-business");
    }

    @Test
    void thermalTemplateUsesTheFullPrintableWidthAndHasItsOwnFooter() throws Exception {
        String html = template("/templates/pdf/cpe-thermal-80.html");
        String fragment = template("/templates/pdf/fragments/cpe-thermal-modern.html");
        assertThat(html).contains("width: 80mm", "padding: 2mm 1.6mm 1.5mm", "word-wrap: break-word");
        assertThat(html).contains(".ticket-shell { background: #ffffff; padding: 0; }");
        assertThat(html).doesNotContain("box-shadow", "#f2f4f7");
        assertThat(fragment).contains("FACTURA ELECTRÓNICA", "Cliente", "Comprobante", "Detalle", "TOTAL");
        assertThat(fragment).contains("issuer-address-value", "QR SUNAT", "DigestValue", "Consulta y descarga");
        assertThat(fragment).contains("Condición de pago", "doc.payment().installments()");
        assertThat(fragment).contains("brandLogoDataUri", "brandAssets.peruFlag", "brandAssets.thanks", "sectionIcons.");
        assertThat(fragment).contains("Representación impresa del comprobante de pago electrónico");
    }

    private String template(String path) throws Exception {
        try (var in = getClass().getResourceAsStream(path)) {
            assertThat(in).isNotNull();
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
