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
import org.junit.jupiter.api.Test;
import pe.com.perubilling.billing.domain.ElectronicDocumentEntity;
import pe.com.perubilling.billing.domain.ElectronicDocumentItemEntity;
import pe.com.perubilling.billing.domain.PaymentInstallmentEntity;
import pe.com.perubilling.billing.domain.PaymentMethod;
import pe.com.perubilling.cpe.domain.DocumentBundle;
import pe.com.perubilling.issuer.domain.IssuerEntity;
import pe.com.perubilling.shared.domain.DocumentType;
import pe.com.perubilling.shared.domain.DocumentPdfLayout;

class DocumentPdfLayoutTest {
    @Test
    void a4RemainsDefaultAndThermalUsesEightyMillimeterWidth() throws Exception {
        var generator = new DocumentPdfGenerator(
                new SunatQrPayloadBuilder(new XmlDigestValueExtractor()),
                new SunatQrCodeGenerator(),
                new pe.com.perubilling.cpe.ubl.AmountInWords(),
                new XmlDigestValueExtractor());
        String publicUrl = "https://example.test/public/v1/documents/preview-token-1234567890";
        byte[] a4 = generator.generate(bundle(), "<root/>".getBytes(), publicUrl);
        byte[] thermal = generator.generate(
                bundle(), "<root/>".getBytes(), publicUrl, DocumentPdfLayout.THERMAL_80);

        String previewDir = System.getProperty("pdf.preview.dir");
        if (previewDir != null && !previewDir.isBlank()) {
            Path directory = Files.createDirectories(Path.of(previewDir));
            Files.write(directory.resolve("ejemplo-factura-a4.pdf"), a4);
            Files.write(directory.resolve("ejemplo-ticket-80mm.pdf"), thermal);
        }

        try (var a4Doc = Loader.loadPDF(a4); var thermalDoc = Loader.loadPDF(thermal)) {
            float a4WidthMm = a4Doc.getPage(0).getMediaBox().getWidth() * 25.4f / 72f;
            float thermalWidthMm = thermalDoc.getPage(0).getMediaBox().getWidth() * 25.4f / 72f;
            assertThat(a4WidthMm).isBetween(209f, 211f);
            assertThat(thermalWidthMm).isBetween(79f, 81f);
            String text = new PDFTextStripper().getText(thermalDoc);
            assertThat(text).contains("FACTURA ELECTRÓNICA");
            assertThat(text).contains("F001-1");
            assertThat(text).contains("QR SUNAT");
            assertThat(text).contains("TOTAL");
            assertThat(text).contains("AV. JAVIER PRADO 1234", "Conserve este comprobante");
            assertThat(thermalDoc.getNumberOfPages()).isEqualTo(1);
            assertThat(a4Doc.getDocumentInformation().getCustomMetadataValue("PeruBilling-Template-Version"))
                    .isEqualTo(DocumentPdfGenerator.PRINT_TEMPLATE_VERSION);
            assertThat(thermalDoc.getDocumentInformation().getCustomMetadataValue("PeruBilling-Template-Version"))
                    .isEqualTo(DocumentPdfGenerator.THERMAL_TEMPLATE_VERSION);
            assertThat(a4Doc.getDocumentInformation().getCustomMetadataValue("PeruBilling-Pdf-Layout"))
                    .isEqualTo("A4");
            assertThat(thermalDoc.getDocumentInformation().getCustomMetadataValue("PeruBilling-Pdf-Layout"))
                    .isEqualTo("THERMAL_80");
        }
    }

    @Test
    void longA4DocumentPaginatesWithoutClippingAndKeepsFooter() throws Exception {
        var generator = new DocumentPdfGenerator(
                new SunatQrPayloadBuilder(new XmlDigestValueExtractor()),
                new SunatQrCodeGenerator(),
                new pe.com.perubilling.cpe.ubl.AmountInWords(),
                new XmlDigestValueExtractor());
        var bundle = bundleWithItems(55);
        byte[] pdf = generator.generate(bundle, "<root/>".getBytes(), "https://example.test/public/long-document");

        try (var document = Loader.loadPDF(pdf)) {
            assertThat(document.getNumberOfPages()).isGreaterThan(1);
            String text = new PDFTextStripper().getText(document);
            assertThat(text).contains("Producto de prueba 55");
            assertThat(text.replaceAll("\\s+", " "))
                    .contains("Representación impresa del comprobante de pago electrónico");
            for (int page = 1; page <= document.getNumberOfPages(); page++) {
                var stripper = new PDFTextStripper();
                stripper.setStartPage(page);
                stripper.setEndPage(page);
                String pageText = stripper.getText(document).replaceAll("\\s+", " ");
                assertThat(pageText).contains("Representación impresa del comprobante de pago electrónico");
                assertThat(pageText).contains("Página " + page + " de " + document.getNumberOfPages());
            }
        }
    }

    @Test
    void shortThermalTicketAvoidsExcessiveBlankPaper() throws Exception {
        var generator = new DocumentPdfGenerator(
                new SunatQrPayloadBuilder(new XmlDigestValueExtractor()),
                new SunatQrCodeGenerator(),
                new pe.com.perubilling.cpe.ubl.AmountInWords(),
                new XmlDigestValueExtractor());
        byte[] thermal = generator.generate(
                bundle(), "<root/>".getBytes(), null, DocumentPdfLayout.THERMAL_80);

        try (var document = Loader.loadPDF(thermal)) {
            float heightMm = document.getPage(0).getMediaBox().getHeight() * 25.4f / 72f;
            assertThat(document.getNumberOfPages()).isEqualTo(1);
            assertThat(heightMm).isBetween(130f, 265f);
        }
    }

    @Test
    void thermalTicketWithSeveralItemsRemainsOneContinuousPage() throws Exception {
        var generator = new DocumentPdfGenerator(
                new SunatQrPayloadBuilder(new XmlDigestValueExtractor()),
                new SunatQrCodeGenerator(),
                new pe.com.perubilling.cpe.ubl.AmountInWords(),
                new XmlDigestValueExtractor());
        byte[] thermal = generator.generate(
                bundleWithItems(18), "<root/>".getBytes(), "https://example.test/public/thermal-long",
                DocumentPdfLayout.THERMAL_80);

        try (var document = Loader.loadPDF(thermal)) {
            assertThat(document.getNumberOfPages()).isEqualTo(1);
            String text = new PDFTextStripper().getText(document);
            assertThat(text).contains("Producto de prueba 18");
            assertThat(text).contains("QR SUNAT");
        }
    }

    @Test
    void receiptAndNotesKeepTheirOwnTitlesAndFootersInBothLayouts() throws Exception {
        var generator = new DocumentPdfGenerator(
                new SunatQrPayloadBuilder(new XmlDigestValueExtractor()),
                new SunatQrCodeGenerator(),
                new pe.com.perubilling.cpe.ubl.AmountInWords(),
                new XmlDigestValueExtractor());
        var variants = List.of(
                new Object[] {DocumentType.RECEIPT, "BOLETA DE VENTA ELECTRÓNICA", "B001-1"},
                new Object[] {DocumentType.CREDIT_NOTE, "NOTA DE CRÉDITO ELECTRÓNICA", "FC01-1"},
                new Object[] {DocumentType.DEBIT_NOTE, "NOTA DE DÉBITO ELECTRÓNICA", "FD01-1"});
        for (Object[] variant : variants) {
            var example = bundle();
            var document = example.document();
            document.setDocumentType((DocumentType) variant[0]);
            document.setFullNumber((String) variant[2]);
            if (document.getDocumentType() == DocumentType.RECEIPT) {
                document.setCustomerDocumentType("1");
                document.setCustomerDocumentNumber("12345678");
            } else {
                document.setReferenceDocumentType("01");
                document.setReferenceDocumentNumber("F001-99");
                document.setReasonCode("01");
                document.setReasonText("Ajuste del comprobante");
            }
            for (DocumentPdfLayout layout : DocumentPdfLayout.values()) {
                byte[] pdf = generator.generate(example, "<root/>".getBytes(), null, layout);
                try (var rendered = Loader.loadPDF(pdf)) {
                    String text = new PDFTextStripper().getText(rendered).replaceAll("\\s+", " ");
                    assertThat(text).contains((String) variant[1], (String) variant[2]);
                    assertThat(text).contains("Representación impresa del comprobante de pago electrónico");
                    if (layout == DocumentPdfLayout.THERMAL_80) {
                        assertThat(rendered.getNumberOfPages()).isEqualTo(1);
                    }
                }
            }
        }
    }

    @Test
    void creditTicketIncludesPendingBalanceAndInstallment() throws Exception {
        var generator = new DocumentPdfGenerator(
                new SunatQrPayloadBuilder(new XmlDigestValueExtractor()),
                new SunatQrCodeGenerator(),
                new pe.com.perubilling.cpe.ubl.AmountInWords(),
                new XmlDigestValueExtractor());
        var base = bundle();
        base.document().setPaymentMethod(PaymentMethod.CREDITO);
        base.document().setPendingAmount(new BigDecimal("118.00"));
        var installment = new PaymentInstallmentEntity();
        installment.setInstallmentNumber(1);
        installment.setDueDate(LocalDate.of(2026, 10, 22));
        installment.setAmount(new BigDecimal("118.00"));
        var credit = new DocumentBundle(base.document(), base.issuer(), base.items(), List.of(installment));
        byte[] pdf = generator.generate(credit, "<root/>".getBytes(), null, DocumentPdfLayout.THERMAL_80);
        try (var rendered = Loader.loadPDF(pdf)) {
            String text = new PDFTextStripper().getText(rendered).replaceAll("\\s+", " ");
            assertThat(text).contains("CONDICIÓN DE PAGO", "CRÉDITO", "Cuota 001", "22/10/2026");
            assertThat(rendered.getNumberOfPages()).isEqualTo(1);
        }
    }

    private DocumentBundle bundle() {
        var d = new ElectronicDocumentEntity();
        d.setDocumentType(DocumentType.INVOICE); d.setSeries("F001"); d.setCorrelativo(1); d.setFullNumber("F001-1");
        d.setIssueDate(LocalDate.of(2026,9,21)); d.setIssueTime(LocalTime.of(17,0)); d.setOperationType("0101"); d.setCurrency("PEN");
        d.setCustomerDocumentType("6"); d.setCustomerDocumentNumber("20100066603"); d.setCustomerName("CLIENTE BRUNO SAC");
        d.setCustomerAddress("AV. CLIENTE 456 - LIMA"); d.setCustomerEmail("pruebas@example.com");
        d.setTaxableAmount(new BigDecimal("100.00")); d.setIgvAmount(new BigDecimal("18.00")); d.setTotalAmount(new BigDecimal("118.00"));
        var i = new ElectronicDocumentItemEntity(); i.setLineNumber(1); i.setSku("BRU-001"); i.setDescription("Producto de prueba"); i.setUnitCode("NIU");
        i.setQuantity(BigDecimal.ONE); i.setUnitValue(new BigDecimal("100.000000")); i.setUnitPrice(new BigDecimal("118.000000")); i.setLineTotalAmount(new BigDecimal("118.00"));
        var issuer = new IssuerEntity(); issuer.setRuc("20123456786"); issuer.setBusinessName("PERUBILLING TEST SAC"); issuer.setTradeName("PERUBILLING TEST"); issuer.setAddress("AV. JAVIER PRADO 1234"); issuer.setUbigeo("150101"); issuer.setEstablishmentCode("0000");
        return new DocumentBundle(d, issuer, List.of(i), List.of());
    }
    private DocumentBundle bundleWithItems(int count) {
        var base = bundle();
        var items = new ArrayList<ElectronicDocumentItemEntity>();
        for (int n = 1; n <= count; n++) {
            var item = new ElectronicDocumentItemEntity();
            item.setLineNumber(n);
            item.setSku("BRU-" + String.format("%03d", n));
            item.setDescription("Producto de prueba " + n + " con descripción para validar paginación A4");
            item.setUnitCode("NIU");
            item.setQuantity(BigDecimal.ONE);
            item.setUnitValue(new BigDecimal("100.000000"));
            item.setUnitPrice(new BigDecimal("118.000000"));
            item.setLineTotalAmount(new BigDecimal("118.00"));
            items.add(item);
        }
        return new DocumentBundle(base.document(), base.issuer(), items, base.installments());
    }

}
