package pe.com.perubilling.cpe.pdf;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.text.TextPosition;
import org.junit.jupiter.api.Test;
import pe.com.perubilling.billing.domain.ElectronicDocumentEntity;
import pe.com.perubilling.billing.domain.ElectronicDocumentItemEntity;
import pe.com.perubilling.cpe.domain.DocumentBundle;
import pe.com.perubilling.issuer.domain.IssuerEntity;
import pe.com.perubilling.shared.domain.DocumentPdfLayout;
import pe.com.perubilling.shared.domain.DocumentType;

/** Tests PDF bytes from the existing OpenHTMLtoPDF engine; no new runtime dependency. */
class ThermalTicketPrintRegressionTest {
    private static final String PUBLIC_URL = "https://billing.example.com/public/v1/documents/token987654321";

    @Test
    void receiptsWithOneFiveAndTwentyItemsFitPrintableRollWidth() throws Exception {
        for (int count : new int[] {1, 5, 20}) {
            byte[] pdf = generator().generate(
                    example(count, false), "<root/>".getBytes(), PUBLIC_URL,
                    DocumentPdfLayout.THERMAL_80);
            verifyPrintGeometry(pdf, count);
        }
    }

    @Test
    void longIssuerCustomerDescriptionsAndAmountsDoNotOverflow() throws Exception {
        byte[] pdf = generator().generate(
                example(5, true), "<root/>".getBytes(), PUBLIC_URL,
                DocumentPdfLayout.THERMAL_80);
        String previewDir = System.getProperty("pdf.preview.dir");
        if (previewDir != null && !previewDir.isBlank()) {
            Files.write(Files.createDirectories(Path.of(previewDir)).resolve("ticket-campos-largos.pdf"), pdf);
        }
        verifyPrintGeometry(pdf, 5);
        try (var document = Loader.loadPDF(pdf)) {
            String text = new PDFTextStripper().getText(document);
            assertThat(text).contains("COMPAÑÍA", "VÁLVULAS", "Dirección:", "TOTAL", "DigestValue");
            assertThat(text).contains("Email:", "9876543.21");
            var glyphs = new ArrayList<TextPosition>();
            new PDFTextStripper() {
                @Override protected void writeString(String value, List<TextPosition> positions) {
                    glyphs.addAll(positions);
                }
            }.getText(document);
            assertThat(glyphs).noneMatch(pos ->
                    pos.getXDirAdj() < 221f && pos.getXDirAdj() + pos.getWidthDirAdj() > 163f
                    && pos.getYDirAdj() < 62f && pos.getYDirAdj() + pos.getHeightDir() > 42f);
        }
    }

    private void verifyPrintGeometry(byte[] pdf, int count) throws Exception {
        try (var document = Loader.loadPDF(pdf)) {
            assertThat(document.getNumberOfPages()).as("continuous roll for %s items", count).isEqualTo(1);
            float width = document.getPage(0).getMediaBox().getWidth();
            float height = document.getPage(0).getMediaBox().getHeight();
            assertThat(width * 25.4f / 72f).isBetween(79.8f, 80.2f);
            assertThat(height * 25.4f / 72f).isBetween(130f, 2000f);

            float[] bounds = {Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY, Float.NEGATIVE_INFINITY};
            PDFTextStripper inspector = new PDFTextStripper() {
                @Override protected void writeString(String value, List<TextPosition> positions) {
                    for (TextPosition pos : positions) {
                        if (!pos.getUnicode().isBlank()) {
                            bounds[0] = Math.min(bounds[0], pos.getXDirAdj());
                            bounds[1] = Math.max(bounds[1], pos.getXDirAdj() + pos.getWidthDirAdj());
                            bounds[2] = Math.max(bounds[2], pos.getYDirAdj() + pos.getHeightDir());
                        }
                    }
                }
            };
            inspector.getText(document);
            assertThat(bounds[0]).isGreaterThanOrEqualTo(5.5f);
            assertThat(bounds[1]).isLessThan(width - 5.5f);
            assertThat(bounds[2]).isLessThan(height - 4f);

            String text = new PDFTextStripper().getText(document);
            assertThat(text).contains("F001-1", "CLIENTE", "COMPROBANTE", "Operación:", "DETALLE", "TOTAL", "QR SUNAT");
            assertThat(text).contains("Representación impresa del comprobante de pago electrónico");
            assertThat(text).contains("Producto " + count);
        }
    }

    private DocumentPdfGenerator generator() {
        return new DocumentPdfGenerator(
                new SunatQrPayloadBuilder(new XmlDigestValueExtractor()),
                new SunatQrCodeGenerator(),
                new pe.com.perubilling.cpe.ubl.AmountInWords(),
                new XmlDigestValueExtractor());
    }

    private DocumentBundle example(int count, boolean longFields) {
        var document = new ElectronicDocumentEntity();
        document.setDocumentType(DocumentType.INVOICE);
        document.setFullNumber("F001-1"); document.setSeries("F001"); document.setCorrelativo(1);
        document.setIssueDate(LocalDate.of(2026, 9, 22)); document.setIssueTime(LocalTime.of(10, 30));
        document.setOperationType("0101"); document.setCurrency("PEN");
        document.setCustomerDocumentType("6"); document.setCustomerDocumentNumber("20100066603");
        document.setCustomerName(longFields
                ? "COMPAÑÍA PERUANA DE SERVICIOS Y SUMINISTROS INDUSTRIALES DEL PACÍFICO SOCIEDAD ANÓNIMA CERRADA"
                : "CLIENTE BRUNO SAC");
        document.setCustomerAddress(longFields
                ? "AV. JAVIER PRADO ESTE 1234 INTERIOR 204 OFICINA 405 URBANIZACIÓN EMPRESARIAL DEL SUR - LIMA - PERÚ"
                : "AV. CLIENTE 456 - LIMA");
        document.setCustomerEmail(longFields
                ? "comprobantes.facturacion.administracion@empresa-industrial-del-pacifico.example.com"
                : "pruebas@example.com");
        document.setTaxableAmount(new BigDecimal(longFields ? "8369951.87" : String.valueOf(100 * count) + ".00"));
        document.setIgvAmount(new BigDecimal(longFields ? "1506591.34" : String.valueOf(18 * count) + ".00"));
        document.setTotalAmount(longFields ? new BigDecimal("9876543.21") : new BigDecimal(String.valueOf(118 * count) + ".00"));

        var issuer = new IssuerEntity();
        issuer.setBusinessName(longFields
                ? "COMPAÑÍA PERUANA DE EQUIPAMIENTO INDUSTRIAL Y VÁLVULAS TÉCNICAS SOCIEDAD ANÓNIMA"
                : "PERUBILLING BRUNO TEST SAC");
        issuer.setTradeName(longFields
                ? "PERUBILLING SUMINISTROS INDUSTRIALES DE LA COSTA Y DE LA SIERRA"
                : "PERUBILLING TEST");
        issuer.setRuc("20654263826");
        issuer.setAddress(longFields
                ? "AVENIDA INDUSTRIAL 1234, INTERIOR 205, EDIFICIO ADMINISTRATIVO CON INGRESO POR CALLE SECUNDARIA"
                : "AV. JAVIER PRADO ESTE 1234");
        issuer.setUbigeo("150101"); issuer.setEstablishmentCode("0000");

        var items = new ArrayList<ElectronicDocumentItemEntity>();
        for (int n = 1; n <= count; n++) {
            var item = new ElectronicDocumentItemEntity();
            item.setLineNumber(n);
            item.setSku("BRU-" + String.format("%03d", n));
            item.setDescription(longFields
                    ? "Producto " + n + " - VÁLVULAS de acero inoxidable con conexión de alta presión, protección anticorrosiva y adaptadores industriales para instalaciones de servicio prolongado"
                    : "Producto " + n + " - Servicio de prueba automatizada Bruno");
            item.setUnitCode("NIU"); item.setQuantity(new BigDecimal("1.000000"));
            item.setUnitValue(longFields ? new BigDecimal("847457.627119") : new BigDecimal("100.000000"));
            item.setUnitPrice(longFields
                    ? new BigDecimal(n == count ? "5876543.210000" : "1000000.000000")
                    : new BigDecimal("118.000000"));
            item.setLineTotalAmount(longFields
                    ? new BigDecimal(n == count ? "5876543.21" : "1000000.00")
                    : new BigDecimal("118.00"));
            items.add(item);
        }
        return new DocumentBundle(document, issuer, items, List.of());
    }
}
