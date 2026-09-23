package pe.com.perubilling.sunat.infrastructure;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.Test;

class ZipSupportTest {
    @Test
    void extractsNormalCdrWithinLimits() {
        byte[] xml = "<ApplicationResponse/>".getBytes(StandardCharsets.UTF_8);
        byte[] zip = ZipSupport.zipSingle("R-test.xml", xml);

        byte[] extracted = ZipSupport.firstXml(
                zip, new ZipSupport.Limits(1024 * 1024, 1024 * 1024, 4, 500));

        assertArrayEquals(xml, extracted);
    }

    @Test
    void rejectsOversizedCompressedPayload() {
        byte[] zip = ZipSupport.zipSingle(
                "R-test.xml", "<ApplicationResponse/>".getBytes(StandardCharsets.UTF_8));

        assertThrows(IllegalArgumentException.class,
                () -> ZipSupport.firstXml(zip, new ZipSupport.Limits(1, 1024, 4, 500)));
    }

    @Test
    void rejectsTooManyEntries() throws Exception {
        byte[] zip = zipEntries(3);
        assertThrows(IllegalArgumentException.class,
                () -> ZipSupport.firstXml(zip, new ZipSupport.Limits(1024 * 1024, 1024, 2, 500)));
    }


    @Test
    void rejectsTooManyEntriesEvenWhenXmlIsFirst() throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(out)) {
            zip.putNextEntry(new ZipEntry("R-test.xml"));
            zip.write("<x/>".getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
            zip.putNextEntry(new ZipEntry("extra-1.txt"));
            zip.write("x".getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
            zip.putNextEntry(new ZipEntry("extra-2.txt"));
            zip.write("x".getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
        }

        assertThrows(IllegalArgumentException.class,
                () -> ZipSupport.firstXml(out.toByteArray(),
                        new ZipSupport.Limits(1024 * 1024, 1024, 2, 500)));
    }

    @Test
    void rejectsOversizedUncompressedXml() {
        byte[] xml = ("<x>" + "A".repeat(5000) + "</x>").getBytes(StandardCharsets.UTF_8);
        byte[] zip = ZipSupport.zipSingle("R-test.xml", xml);

        assertThrows(IllegalArgumentException.class,
                () -> ZipSupport.firstXml(zip, new ZipSupport.Limits(1024 * 1024, 1000, 4, 500)));
    }

    @Test
    void rejectsSuspiciousCompressionRatio() {
        byte[] xml = ("<x>" + "A".repeat(20_000) + "</x>").getBytes(StandardCharsets.UTF_8);
        byte[] zip = ZipSupport.zipSingle("R-test.xml", xml);

        assertThrows(IllegalArgumentException.class,
                () -> ZipSupport.firstXml(zip, new ZipSupport.Limits(
                        1024 * 1024, 100_000, 4, 2)));
    }

    @Test
    void rejectsTraversalEntryName() throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(out)) {
            zip.putNextEntry(new ZipEntry("../R-test.xml"));
            zip.write("<x/>".getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
        }

        assertThrows(IllegalArgumentException.class,
                () -> ZipSupport.firstXml(out.toByteArray(),
                        new ZipSupport.Limits(1024 * 1024, 1024, 4, 500)));
    }

    private byte[] zipEntries(int count) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(out)) {
            for (int i = 0; i < count; i++) {
                zip.putNextEntry(new ZipEntry("entry-" + i + ".txt"));
                zip.write("x".getBytes(StandardCharsets.UTF_8));
                zip.closeEntry();
            }
        }
        return out.toByteArray();
    }
}
