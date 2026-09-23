package pe.com.perubilling.cpe.pdf;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import java.awt.image.BufferedImage;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class SunatQrCodeGenerator {
    public BufferedImage generate(String payload, int pixels) {
        try {
            var hints = Map.of(
                    EncodeHintType.CHARACTER_SET, StandardCharsets.UTF_8.name(),
                    EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.Q,
                    EncodeHintType.MARGIN, 4);
            var matrix = new QRCodeWriter().encode(payload, BarcodeFormat.QR_CODE, pixels, pixels, hints);
            return MatrixToImageWriter.toBufferedImage(matrix);
        } catch (Exception ex) {
            throw new IllegalStateException("No se pudo generar el código QR SUNAT", ex);
        }
    }
}
