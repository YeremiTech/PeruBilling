package pe.com.perubilling.cpe.pdf;

import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Path2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.Base64;
import java.util.Map;
import javax.imageio.ImageIO;

/**
 * Recursos visuales embebidos para las representaciones PDF.
 *
 * <p>El isotipo parte del SVG de marca incluido en
 * {@code src/main/resources/branding/perubilling-mark.svg}. Se rasteriza de forma
 * determinista para evitar dependencias externas y mantener compatibilidad con
 * OpenHTMLToPDF/PDFBox en servidores headless.</p>
 */
final class PdfBrandAssets {
    static final String PRIMARY = "#053E58";
    static final String SECONDARY = "#0B5675";
    static final String ACCENT = "#1AB09B";

    static final String BRAND_LOGO_DATA_URI = createBrandLogoDataUri(1.0f);
    static final String WATERMARK_LOGO_DATA_URI = createBrandLogoDataUri(0.035f);

    static final Map<String, String> SECTION_ICONS = Map.of(
            "customer", createSectionIconDataUri(SectionIcon.CUSTOMER),
            "document", createSectionIconDataUri(SectionIcon.DOCUMENT),
            "products", createSectionIconDataUri(SectionIcon.PRODUCTS),
            "reference", createSectionIconDataUri(SectionIcon.REFERENCE),
            "observations", createSectionIconDataUri(SectionIcon.OBSERVATIONS),
            "payments", createSectionIconDataUri(SectionIcon.PAYMENTS),
            "totals", createSectionIconDataUri(SectionIcon.TOTALS),
            "validation", createSectionIconDataUri(SectionIcon.VALIDATION));

    private PdfBrandAssets() {}

    private static String createSectionIconDataUri(SectionIcon icon) {
        try {
            int size = 96;
            BufferedImage image = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
            Graphics2D graphics = image.createGraphics();
            graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            graphics.scale(size / 24.0, size / 24.0);
            graphics.setColor(Color.WHITE);
            graphics.setStroke(new BasicStroke(1.8f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));

            switch (icon) {
                case CUSTOMER -> {
                    graphics.fillOval(8, 4, 8, 8);
                    graphics.fillRoundRect(4, 14, 16, 7, 4, 4);
                }
                case DOCUMENT -> {
                    graphics.drawRoundRect(6, 3, 12, 18, 2, 2);
                    graphics.drawLine(9, 9, 15, 9);
                    graphics.drawLine(9, 13, 15, 13);
                    graphics.drawLine(9, 17, 13, 17);
                }
                case PRODUCTS -> {
                    graphics.drawLine(12, 3, 20, 7);
                    graphics.drawLine(20, 7, 12, 11);
                    graphics.drawLine(12, 11, 4, 7);
                    graphics.drawLine(4, 7, 12, 3);
                    graphics.drawLine(4, 7, 4, 16);
                    graphics.drawLine(12, 11, 12, 21);
                    graphics.drawLine(20, 7, 20, 16);
                    graphics.drawLine(4, 16, 12, 21);
                    graphics.drawLine(20, 16, 12, 21);
                }
                case REFERENCE -> {
                    graphics.drawRoundRect(4, 5, 10, 14, 2, 2);
                    graphics.drawRoundRect(10, 3, 10, 14, 2, 2);
                    graphics.drawLine(12, 8, 18, 8);
                    graphics.drawLine(12, 12, 18, 12);
                }
                case OBSERVATIONS -> {
                    graphics.drawRoundRect(4, 5, 16, 12, 3, 3);
                    graphics.drawLine(8, 9, 16, 9);
                    graphics.drawLine(8, 13, 14, 13);
                    graphics.drawLine(8, 17, 6, 21);
                }
                case PAYMENTS -> {
                    graphics.drawRoundRect(3, 6, 18, 12, 2, 2);
                    graphics.drawLine(5, 10, 19, 10);
                    graphics.drawLine(7, 15, 12, 15);
                }
                case TOTALS -> {
                    graphics.drawRoundRect(7, 3, 10, 18, 2, 2);
                    graphics.drawRect(9, 5, 6, 4);
                    graphics.fillOval(9, 12, 2, 2);
                    graphics.fillOval(13, 12, 2, 2);
                    graphics.fillOval(9, 16, 2, 2);
                    graphics.fillOval(13, 16, 2, 2);
                }
                case VALIDATION -> {
                    graphics.fillRect(4, 4, 6, 6);
                    graphics.fillRect(14, 4, 6, 6);
                    graphics.fillRect(4, 14, 6, 6);
                    graphics.fillRect(13, 13, 3, 3);
                    graphics.fillRect(18, 13, 2, 5);
                    graphics.fillRect(13, 18, 5, 2);
                    graphics.fillRect(20, 20, 2, 2);
                }
            }

            graphics.dispose();
            return pngDataUri(image);
        } catch (Exception ex) {
            throw new IllegalStateException("No se pudieron preparar los iconos del PDF", ex);
        }
    }

    private static String createBrandLogoDataUri(float opacity) {
        try {
            int size = 512;
            BufferedImage image = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
            Graphics2D graphics = image.createGraphics();
            graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            graphics.scale(size / 24.0, size / 24.0);
            graphics.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, opacity));

            // Geometría del SVG proporcionado por el usuario, con la paleta usada en pymex-erp-backend.
            fill(graphics, PRIMARY,
                    21.715, 5.61, 17.732, 7.92, 16.836, 7.92, 12.44, 5.384,
                    11.543, 5.384, 7.156, 7.92, 6.26, 7.92, 2.276, 5.617, 12.002, 0);
            fill(graphics, SECONDARY,
                    18.641, 9.467, 18.203, 10.237, 18.203, 15.309, 17.758, 16.079,
                    13.33, 18.589, 12.885, 19.366, 12.885, 23.973, 17.314, 21.437,
                    22.624, 18.39, 22.624, 7.157);
            fill(graphics, ACCENT,
                    10.98, 18.941, 10.675, 18.589, 6.246, 16.073, 5.815, 15.309,
                    5.815, 10.231, 5.363, 9.474, 4.912, 9.214, 1.38, 7.158,
                    1.38, 18.39, 6.691, 21.437, 11.126, 24, 11.126, 19.392);

            graphics.dispose();
            return pngDataUri(image);
        } catch (Exception ex) {
            throw new IllegalStateException("No se pudo preparar el isotipo de PeruBilling", ex);
        }
    }

    private static String pngDataUri(BufferedImage image) throws Exception {
        try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            if (!ImageIO.write(image, "png", output)) {
                throw new IllegalStateException("No existe un codificador PNG disponible");
            }
            return "data:image/png;base64," + Base64.getEncoder().encodeToString(output.toByteArray());
        }
    }

    private static void fill(Graphics2D graphics, String color, double... points) {
        Path2D.Double path = new Path2D.Double();
        path.moveTo(points[0], points[1]);
        for (int index = 2; index < points.length; index += 2) {
            path.lineTo(points[index], points[index + 1]);
        }
        path.closePath();
        graphics.setColor(Color.decode(color));
        graphics.fill(path);
    }

    private enum SectionIcon {
        CUSTOMER,
        DOCUMENT,
        PRODUCTS,
        REFERENCE,
        OBSERVATIONS,
        PAYMENTS,
        TOTALS,
        VALIDATION
    }
}
