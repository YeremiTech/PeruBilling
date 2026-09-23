package pe.com.perubilling.cpe.pdf;

import com.openhtmltopdf.outputdevice.helper.BaseRendererBuilder;
import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import java.io.ByteArrayOutputStream;
import java.util.Base64;
import java.util.Locale;
import java.util.List;
import javax.imageio.ImageIO;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocumentInformation;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.text.TextPosition;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;
import pe.com.perubilling.cpe.domain.DocumentBundle;
import pe.com.perubilling.cpe.ubl.AmountInWords;
import pe.com.perubilling.shared.domain.DocumentPdfLayout;

@Component
public class DocumentPdfGenerator {
    private static final String TEMPLATE_A4 = "cpe-a4";
    private static final String TEMPLATE_THERMAL_80 = "cpe-thermal-80";
    static final String PRINT_TEMPLATE_VERSION = "PRINT-V21-20260923";
    static final String THERMAL_TEMPLATE_VERSION = "THERMAL-PRINT-V21-20260923";

    private final SunatQrPayloadBuilder qrPayloadBuilder;
    private final SunatQrCodeGenerator qrGenerator;
    private final AmountInWords amountInWords;
    private final XmlDigestValueExtractor digestExtractor;
    private final TemplateEngine templateEngine;

    @Value("${perubilling.pdf.thermal.thank-you:}")
    private String thermalThankYou;

    public DocumentPdfGenerator(
            SunatQrPayloadBuilder qrPayloadBuilder,
            SunatQrCodeGenerator qrGenerator,
            AmountInWords amountInWords,
            XmlDigestValueExtractor digestExtractor) {
        this.qrPayloadBuilder = qrPayloadBuilder;
        this.qrGenerator = qrGenerator;
        this.amountInWords = amountInWords;
        this.digestExtractor = digestExtractor;
        this.templateEngine = templateEngine();
    }

    public byte[] generate(DocumentBundle bundle, byte[] signedXml) {
        return generate(bundle, signedXml, null, DocumentPdfLayout.A4);
    }

    public byte[] generate(DocumentBundle bundle, byte[] signedXml, String publicAccessUrl) {
        return generate(bundle, signedXml, publicAccessUrl, DocumentPdfLayout.A4);
    }

    public byte[] generate(
            DocumentBundle bundle,
            byte[] signedXml,
            String publicAccessUrl,
            DocumentPdfLayout layout) {
        try {
            DocumentPdfLayout safeLayout = layout == null ? DocumentPdfLayout.A4 : layout;
            String payload = qrPayloadBuilder.build(bundle, signedXml);
            String qrDataUri = qrDataUri(payload);
            String digest = digestExtractor.extract(signedXml);
            String amountText = amountInWords.convert(
                    bundle.document().getTotalAmount(), bundle.document().getCurrency());

            var model = DocumentPdfViewModel.from(
                    bundle, amountText, qrDataUri, digest, publicAccessUrl);

            Context context = new Context(Locale.forLanguageTag("es-PE"));
            context.setVariable("doc", model);
            context.setVariable("layout", safeLayout.name());
            context.setVariable("brandLogoDataUri", PdfBrandAssets.BRAND_LOGO_DATA_URI);
            context.setVariable("sectionIcons", PdfBrandAssets.SECTION_ICONS);
            context.setVariable("metaIcons", PdfBrandAssets.META_ICONS);
            context.setVariable("footerIcons", PdfBrandAssets.FOOTER_ICONS);
            context.setVariable("brandAssets", PdfBrandAssets.BRAND_IMAGES);
            context.setVariable("thermalThanks", thermalThankYou == null ? "" : thermalThankYou.strip());
            String html = templateEngine.process(template(safeLayout), context);

            byte[] result = safeLayout == DocumentPdfLayout.THERMAL_80
                    ? renderThermalSinglePage(html, thermalHeightMm(model))
                    : render(html, null);
            return stampTemplateVersion(result, safeLayout);
        } catch (Exception ex) {
            throw new IllegalStateException("No se pudo generar la representación PDF", ex);
        }
    }

    private byte[] stampTemplateVersion(byte[] pdf, DocumentPdfLayout layout) throws Exception {
        try (var document = Loader.loadPDF(pdf);
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            PDDocumentInformation info = document.getDocumentInformation();
            if (info == null) info = new PDDocumentInformation();
            info.setCustomMetadataValue("PeruBilling-Template-Version",
                    layout == DocumentPdfLayout.THERMAL_80 ? THERMAL_TEMPLATE_VERSION : PRINT_TEMPLATE_VERSION);
            info.setCustomMetadataValue("PeruBilling-Pdf-Layout", layout.name());
            document.setDocumentInformation(info);
            document.save(out);
            return out.toByteArray();
        }
    }

    private String template(DocumentPdfLayout layout) {
        return layout == DocumentPdfLayout.THERMAL_80 ? TEMPLATE_THERMAL_80 : TEMPLATE_A4;
    }

    /**
     * A receipt has a variable length: it should occupy one 80 mm-wide page,
     * with enough space for its footer but no large blank trailer.
     * We first render with a conservative estimate, increase if necessary,
     * then trim to the last printed text (the legal notice follows the images).
     */
    private byte[] renderThermalSinglePage(String html, float estimatedHeightMm) throws Exception {
        float height = Math.min(2000f, Math.max(175f, estimatedHeightMm));
        byte[] pdf = render(html, height);
        int pages = pageCount(pdf);
        for (int attempt = 0; pages > 1 && attempt < 7 && height < 2000f; attempt++) {
            height = Math.min(2000f, Math.max(height * 1.4f, height + pages * 25f));
            pdf = render(html, height);
            pages = pageCount(pdf);
        }
        if (pages != 1) {
            throw new IllegalStateException("El ticket supera el máximo de 2000 mm: "
                    + pages + " páginas. No se devuelve un PDF cortado.");
        }

        // The legal footer is the last printed element. Retain 4 mm for the
        // bottom padding and cutter; retry with additional clearance if the
        // layout needs an extra few millimeters after reflow.
        float desired = Math.max(105f, Math.min(height, textBottomMm(pdf) + 4f));
        if (desired < height - 5f) {
            for (int attempt = 0; attempt < 3; attempt++) {
                byte[] compact = render(html, desired);
                if (pageCount(compact) == 1) {
                    return compact;
                }
                desired = Math.min(height, desired + 5f);
            }
        }
        return pdf;
    }

    private float textBottomMm(byte[] pdf) throws Exception {
        try (var document = Loader.loadPDF(pdf)) {
            final float[] lastTextPt = {0f};
            PDFTextStripper stripper = new PDFTextStripper() {
                @Override
                protected void writeString(String text, List<TextPosition> positions) {
                    for (TextPosition position : positions) {
                        lastTextPt[0] = Math.max(lastTextPt[0],
                                position.getYDirAdj() + position.getHeightDir());
                    }
                }
            };
            stripper.setStartPage(1);
            stripper.setEndPage(1);
            stripper.getText(document);
            if (lastTextPt[0] <= 0f) {
                return 2000f; // An unknown text layout must never be cropped blindly.
            }
            return lastTextPt[0] * 25.4f / 72f;
        }
    }

    private byte[] render(String html, Float thermalHeightMm) throws Exception {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            PdfRendererBuilder builder = new PdfRendererBuilder();
            builder.useFastMode();
            if (thermalHeightMm != null) {
                builder.useDefaultPageSize(80f, thermalHeightMm, BaseRendererBuilder.PageSizeUnits.MM);
            }
            builder.withHtmlContent(html, null);
            builder.toStream(out);
            builder.run();
            return out.toByteArray();
        }
    }

    private int pageCount(byte[] pdf) throws Exception {
        try (var document = Loader.loadPDF(pdf)) {
            return document.getNumberOfPages();
        }
    }

    private float thermalHeightMm(DocumentPdfViewModel model) {
        float height = 172f;

        height += wrappedExtra(model.issuer().tradeName(), 26, 3.8f);
        height += wrappedExtra(model.issuer().businessName(), 30, 3.4f);
        height += wrappedExtra(model.issuer().address(), 34, 3.6f);
        height += wrappedExtra(model.customer().name(), 34, 3.2f);
        height += wrappedExtra(model.customer().address(), 34, 3.4f);
        height += wrappedExtra(model.customer().email(), 34, 3.0f);

        for (var item : model.items()) {
            height += 14.5f;
            height += wrappedExtra(item.description(), 36, 3.6f);
            height += wrappedExtra(item.sku(), 24, 2.8f);
        }

        height += model.totals().size() * 5.2f;

        if (model.reference() != null) {
            height += 15f;
            height += wrappedExtra(model.reference().reason(), 36, 3.4f);
        }

        if (model.payment() != null
                && ("CRÉDITO".equals(model.payment().method())
                    || !model.payment().installments().isEmpty())) {
            height += 14f + model.payment().installments().size() * 6.2f;
        }

        if (!model.publicAccessUrl().isBlank()) {
            height += 10f + wrappedExtra(model.publicAccessUrl(), 32, 3.0f);
        }

        height += 42f;
        return Math.max(220f, Math.min(height, 2200f));
    }

    private float wrappedExtra(String value, int charsPerLine, float mmPerExtraLine) {
        if (value == null || value.isBlank()) return 0f;
        int lines = Math.max(1, (value.trim().length() + charsPerLine - 1) / charsPerLine);
        return Math.max(0, lines - 1) * mmPerExtraLine;
    }

    private String qrDataUri(String payload) throws Exception {
        var image = qrGenerator.generate(payload, 420);
        try (ByteArrayOutputStream qr = new ByteArrayOutputStream()) {
            if (!ImageIO.write(image, "PNG", qr)) {
                throw new IllegalStateException("No existe un codificador PNG disponible");
            }
            return "data:image/png;base64," + Base64.getEncoder().encodeToString(qr.toByteArray());
        }
    }

    private static TemplateEngine templateEngine() {
        ClassLoaderTemplateResolver resolver = new ClassLoaderTemplateResolver();
        resolver.setPrefix("templates/pdf/");
        resolver.setSuffix(".html");
        resolver.setTemplateMode(TemplateMode.HTML);
        resolver.setCharacterEncoding("UTF-8");
        resolver.setCacheable(true);

        TemplateEngine engine = new TemplateEngine();
        engine.setTemplateResolver(resolver);
        return engine;
    }
}
