package pe.com.perubilling.cpe.pdf;

import com.openhtmltopdf.outputdevice.helper.BaseRendererBuilder;
import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import java.io.ByteArrayOutputStream;
import java.util.Base64;
import java.util.Locale;
import javax.imageio.ImageIO;
import org.apache.pdfbox.Loader;
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

    private final SunatQrPayloadBuilder qrPayloadBuilder;
    private final SunatQrCodeGenerator qrGenerator;
    private final AmountInWords amountInWords;
    private final XmlDigestValueExtractor digestExtractor;
    private final TemplateEngine templateEngine;

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
            context.setVariable("watermarkLogoDataUri", PdfBrandAssets.WATERMARK_LOGO_DATA_URI);
            context.setVariable("sectionIcons", PdfBrandAssets.SECTION_ICONS);
            String html = templateEngine.process(template(safeLayout), context);

            if (safeLayout == DocumentPdfLayout.THERMAL_80) {
                return renderThermalSinglePage(html, thermalHeightMm(model));
            }
            return render(html, null);
        } catch (Exception ex) {
            throw new IllegalStateException("No se pudo generar la representación PDF", ex);
        }
    }

    private String template(DocumentPdfLayout layout) {
        return layout == DocumentPdfLayout.THERMAL_80 ? TEMPLATE_THERMAL_80 : TEMPLATE_A4;
    }

    private byte[] renderThermalSinglePage(String html, float estimatedHeightMm) throws Exception {
        float height = Math.max(245f, estimatedHeightMm);
        byte[] pdf = render(html, height);

        // OpenHTMLToPDF puede desplazar una última línea a una segunda página por
        // redondeos de CSS/pt. Re-renderizamos con margen incremental hasta que el
        // ticket quede en una sola hoja continua, que es lo esperado en rollo 80 mm.
        for (int attempt = 0; attempt < 6 && pageCount(pdf) > 1; attempt++) {
            int pages = pageCount(pdf);
            height = Math.min(2000f, height + Math.max(18f, pages * 12f));
            pdf = render(html, height);
        }
        return pdf;
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
        // OpenHTMLToPDF needs a concrete page height for a roll-style PDF. Estimate from
        // actual content instead of using a large fixed base, which otherwise leaves an
        // unnecessarily long blank tail on short tickets.
        float height = 166f;

        height += wrappedExtra(model.issuer().tradeName(), 28, 3.5f);
        height += wrappedExtra(model.issuer().businessName(), 34, 3.0f);
        height += wrappedExtra(model.issuer().address(), 42, 3.0f);
        height += wrappedExtra(model.customer().name(), 40, 3.0f);
        height += wrappedExtra(model.customer().address(), 44, 3.0f);
        height += wrappedExtra(model.customer().email(), 44, 2.5f);

        for (var item : model.items()) {
            height += 12.5f;
            height += wrappedExtra(item.description(), 42, 3.4f);
            height += wrappedExtra(item.sku(), 28, 2.4f);
        }

        height += model.totals().size() * 4.8f;

        if (model.reference() != null) {
            height += 13f;
            height += wrappedExtra(model.reference().reason(), 44, 3.2f);
        }

        if (model.payment() != null
                && (!model.payment().installments().isEmpty()
                    || !model.payment().pendingAmount().isBlank())) {
            height += 12f + model.payment().installments().size() * 5.8f;
        }

        height += wrappedExtra(model.observationText(), 48, 3.0f);
        if (!model.publicAccessUrl().isBlank()) {
            height += 8f + wrappedExtra(model.publicAccessUrl(), 38, 2.7f);
        }

        // Espacio para QR, bloque final y redondeos del renderer.
        height += 38f;
        return Math.max(245f, Math.min(height, 2000f));
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
