package pe.com.perubilling.cpe.pdf;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.RoundingMode;
import java.text.Normalizer;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.pdmodel.graphics.image.LosslessFactory;
import org.springframework.stereotype.Component;
import pe.com.perubilling.cpe.domain.DocumentBundle;

@Component
public class DocumentPdfGenerator {
    private static final float TOP = 790;
    private static final float BOTTOM = 80;
    private final SunatQrPayloadBuilder qrPayloadBuilder;
    private final SunatQrCodeGenerator qrGenerator;

    public DocumentPdfGenerator(SunatQrPayloadBuilder qrPayloadBuilder, SunatQrCodeGenerator qrGenerator) {
        this.qrPayloadBuilder = qrPayloadBuilder;
        this.qrGenerator = qrGenerator;
    }

    public byte[] generate(DocumentBundle bundle, byte[] signedXml) {
        return generate(bundle, signedXml, null);
    }

    public byte[] generate(DocumentBundle bundle, byte[] signedXml, String publicAccessUrl) {
        try (PDDocument pdf = new PDDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            PDType1Font regular = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
            PDType1Font bold = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);

            PageState state = newPage(pdf, bundle, regular, bold, 1);
            int pageNumber = 1;

            for (var item : bundle.items()) {
                if (state.y() < BOTTOM + 55) {
                    state.stream().close();
                    pageNumber++;
                    state = newPage(pdf, bundle, regular, bold, pageNumber);
                }
                String line = fit(item.getQuantity().stripTrailingZeros().toPlainString(), 8) + " " +
                        fit(item.getUnitCode(), 8) + " " +
                        fit(item.getDescription(), 46) + " " +
                        fit(item.getUnitValue().setScale(2, RoundingMode.HALF_UP).toPlainString(), 12) + " " +
                        fit(item.getLineTotalAmount().setScale(2, RoundingMode.HALF_UP).toPlainString(), 12);
                float y = text(state.stream(), regular, 8, 50, state.y(), line);
                state = new PageState(state.page(), state.stream(), y);
            }

            // Reserva espacio para totales, QR y leyenda de consulta.
            if (state.y() < BOTTOM + 210) {
                state.stream().close();
                pageNumber++;
                state = newPage(pdf, bundle, regular, bold, pageNumber);
            }

            float y = state.y() - 10;
            y = text(state.stream(), regular, 10, 330, y, "Op. gravadas IGV: " + bundle.document().getTaxableAmount());
            if (bundle.document().getIvapTaxableAmount().signum() > 0) {
                y = text(state.stream(), regular, 10, 330, y, "Op. gravadas IVAP: " + bundle.document().getIvapTaxableAmount());
            }
            if (bundle.document().getExoneratedAmount().signum() > 0) {
                y = text(state.stream(), regular, 10, 330, y, "Op. exoneradas: " + bundle.document().getExoneratedAmount());
            }
            if (bundle.document().getUnaffectedAmount().signum() > 0) {
                y = text(state.stream(), regular, 10, 330, y, "Op. inafectas: " + bundle.document().getUnaffectedAmount());
            }
            if (bundle.document().getExportAmount().signum() > 0) {
                y = text(state.stream(), regular, 10, 330, y, "Op. exportación: " + bundle.document().getExportAmount());
            }
            if (bundle.document().getFreeAmount().signum() > 0) {
                y = text(state.stream(), regular, 10, 330, y, "Op. gratuitas: " + bundle.document().getFreeAmount());
            }
            if (bundle.document().getIgvAmount().signum() > 0) {
                y = text(state.stream(), regular, 10, 330, y, "IGV: " + bundle.document().getIgvAmount());
            }
            if (bundle.document().getIvapAmount().signum() > 0) {
                y = text(state.stream(), regular, 10, 330, y, "IVAP: " + bundle.document().getIvapAmount());
            }
            if (bundle.document().getIcbperAmount().signum() > 0) {
                y = text(state.stream(), regular, 10, 330, y, "ICBPER: " + bundle.document().getIcbperAmount());
            }
            if (bundle.document().getAllowanceTotalAmount() != null
                    && bundle.document().getAllowanceTotalAmount().signum() > 0) {
                y = text(state.stream(), regular, 10, 330, y,
                        "Descuentos: -" + bundle.document().getAllowanceTotalAmount());
            }
            if (bundle.document().getChargeTotalAmount() != null
                    && bundle.document().getChargeTotalAmount().signum() > 0) {
                y = text(state.stream(), regular, 10, 330, y,
                        "Otros cargos: " + bundle.document().getChargeTotalAmount());
            }
            text(state.stream(), bold, 12, 330, y, "TOTAL: " + bundle.document().getCurrency() + " " + bundle.document().getTotalAmount());

            String payload = qrPayloadBuilder.build(bundle, signedXml);
            var qr = LosslessFactory.createFromImage(pdf, qrGenerator.generate(payload, 320));
            state.stream().drawImage(qr, 50, 52, 92, 92);
            text(state.stream(), bold, 8, 155, 128, "Código QR - SUNAT");
            text(state.stream(), regular, 7, 155, 112,
                    "Contiene RUC, tipo, numeración, IGV, total, fecha, adquirente y valor resumen.");
            text(state.stream(), regular, 7, 155, 96,
                    "Consulte la validez del CPE y conserve el XML firmado y la CDR.");
            if (publicAccessUrl != null && !publicAccessUrl.isBlank()) {
                text(state.stream(), bold, 7, 155, 80, "Consulta y descarga del comprobante:");
                String normalizedUrl = publicAccessUrl.trim();
                int cut = Math.min(72, normalizedUrl.length());
                text(state.stream(), regular, 6.5f, 155, 67, normalizedUrl.substring(0, cut));
                if (cut < normalizedUrl.length()) {
                    text(state.stream(), regular, 6.5f, 155, 56, normalizedUrl.substring(cut, Math.min(cut + 72, normalizedUrl.length())));
                }
            }
            text(state.stream(), regular, 7, 50, 38,
                    "Representación impresa del comprobante de pago electrónico.");
            state.stream().close();

            pdf.save(out);
            return out.toByteArray();
        } catch (IOException ex) {
            throw new IllegalStateException("No se pudo generar la representación PDF", ex);
        }
    }

    private PageState newPage(PDDocument pdf, DocumentBundle bundle, PDType1Font regular,
                              PDType1Font bold, int pageNumber) throws IOException {
        PDPage page = new PDPage(PDRectangle.A4);
        pdf.addPage(page);
        PDPageContentStream cs = new PDPageContentStream(pdf, page);
        float y = TOP;

        y = text(cs, bold, 16, 50, y, safe(bundle.issuer().getBusinessName()));
        y = text(cs, regular, 10, 50, y - 4, "RUC: " + bundle.issuer().getRuc());
        y = text(cs, regular, 10, 50, y, safe(bundle.issuer().getAddress()));
        text(cs, bold, 14, 350, TOP, title(bundle));
        text(cs, bold, 12, 350, TOP - 20, bundle.document().getFullNumber());
        text(cs, regular, 8, 500, TOP - 40, "Pag. " + pageNumber);

        y -= 24;
        y = text(cs, bold, 10, 50, y, "Cliente: " + safe(bundle.document().getCustomerName()));
        y = text(cs, regular, 10, 50, y, "Documento: " + safe(bundle.document().getCustomerDocumentNumber()));
        if (bundle.document().getCustomerCountryCode() != null && !bundle.document().getCustomerCountryCode().isBlank()) {
            y = text(cs, regular, 10, 50, y, "País adquirente: " + bundle.document().getCustomerCountryCode());
        }
        y = text(cs, regular, 10, 50, y,
                "Fecha: " + bundle.document().getIssueDate() + "   Moneda: " + bundle.document().getCurrency());
        y -= 10;
        y = text(cs, bold, 9, 50, y,
                "Cant.    Unidad    Descripcion                                      V.Unit.        Total");
        return new PageState(page, cs, y);
    }

    private String title(DocumentBundle bundle) {
        return switch (bundle.document().getDocumentType()) {
            case INVOICE -> "FACTURA ELECTRONICA";
            case RECEIPT -> "BOLETA ELECTRONICA";
            case CREDIT_NOTE -> "NOTA DE CREDITO";
            case DEBIT_NOTE -> "NOTA DE DEBITO";
        };
    }

    private float text(PDPageContentStream cs, PDType1Font font, float size,
                       float x, float y, String value) throws IOException {
        cs.beginText();
        cs.setFont(font, size);
        cs.newLineAtOffset(x, y);
        cs.showText(sanitize(value));
        cs.endText();
        return y - (size + 4);
    }

    private String fit(String value, int max) {
        String v = safe(value);
        return v.length() <= max
                ? String.format("%-" + max + "s", v)
                : v.substring(0, Math.max(0, max - 1)) + "~";
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    private String sanitize(String value) {
        String normalized = Normalizer.normalize(safe(value), Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .replace('\n', ' ')
                .replace('\r', ' ')
                .replace("\u0000", "");
        return normalized.replaceAll("[^\\x20-\\x7E]", "?");
    }

    private record PageState(PDPage page, PDPageContentStream stream, float y) {}
}
