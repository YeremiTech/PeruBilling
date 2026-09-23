package pe.com.perubilling.cpe.pdf;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class SunatQrCodeGeneratorTest {
    @Test
    void generatesSquareQrImage() {
        var image = new SunatQrCodeGenerator().generate("20100066603|01|F001|1|18.00|118.00|2026-09-12|6|20123456789|abc|", 300);
        assertThat(image.getWidth()).isEqualTo(300);
        assertThat(image.getHeight()).isEqualTo(300);
        // A clear quiet zone is needed to scan the QR printed on 80 mm paper.
        assertThat(image.getRGB(0, 0) & 0xffffff).isEqualTo(0xffffff);
        assertThat(image.getRGB(299, 299) & 0xffffff).isEqualTo(0xffffff);
    }
}
