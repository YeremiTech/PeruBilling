package pe.com.perubilling.cpe.pdf;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Base64;
import org.junit.jupiter.api.Test;

class PdfBrandAssetsTest {
    @Test
    void brandLogoAndSectionIconsAreEmbeddedPngDataUris() {
        assertThat(PdfBrandAssets.BRAND_LOGO_DATA_URI).startsWith("data:image/png;base64,");
        assertThat(PdfBrandAssets.SECTION_ICONS).containsKeys(
                "customer", "document", "products", "reference",
                "payments", "totals", "validation");
        assertThat(PdfBrandAssets.BRAND_IMAGES).containsKeys("hero", "tagline", "thanks", "peruFlag");
        assertThat(PdfBrandAssets.META_ICONS).containsKeys("ruc", "address", "ubigeo", "money");
        assertThat(PdfBrandAssets.FOOTER_ICONS).containsKeys("leaf", "shield", "globe");
        assertThat(PdfBrandAssets.BRAND_IMAGES.get("heroA4")).startsWith("data:image/jpeg;base64,");

        String payload = PdfBrandAssets.BRAND_LOGO_DATA_URI.substring("data:image/png;base64,".length());
        assertThat(Base64.getDecoder().decode(payload)).isNotEmpty();
        String heroPayload = PdfBrandAssets.BRAND_IMAGES.get("hero").substring("data:image/png;base64,".length());
        assertThat(Base64.getDecoder().decode(heroPayload)).isNotEmpty();
    }

    @Test
    void canonicalSvgUsesTheSameThreeBrandColorsAsThePdfRenderer() throws Exception {
        try (var input = getClass().getResourceAsStream("/branding/perubilling-mark.svg")) {
            assertThat(input).isNotNull();
            String svg = new String(input.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
            assertThat(svg).contains("#053E58");
            assertThat(svg).contains("#0B5675");
            assertThat(svg).contains("#1AB09B");
            assertThat(svg.split("<path", -1)).hasSize(4);
        }
    }
}
