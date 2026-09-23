package pe.com.perubilling.sunat.infrastructure;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

final class ZipSupport {
    private ZipSupport() {}

    record Limits(long maxZipBytes, long maxXmlBytes, int maxEntries, long maxCompressionRatio) {
        Limits {
            if (maxZipBytes <= 0 || maxXmlBytes <= 0 || maxEntries <= 0 || maxCompressionRatio <= 0) {
                throw new IllegalArgumentException("Los límites ZIP/CDR deben ser mayores que cero");
            }
        }
    }

    static byte[] zipSingle(String entryName, byte[] content) {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            try (ZipOutputStream zip = new ZipOutputStream(out)) {
                ZipEntry entry = new ZipEntry(entryName);
                zip.putNextEntry(entry);
                zip.write(content);
                zip.closeEntry();
            }
            return out.toByteArray();
        } catch (IOException ex) {
            throw new IllegalStateException("No se pudo crear ZIP CPE", ex);
        }
    }

    static byte[] firstXml(byte[] zipBytes, Limits limits) {
        if (zipBytes == null || zipBytes.length == 0) {
            throw new IllegalArgumentException("CDR ZIP vacío");
        }
        if (zipBytes.length > limits.maxZipBytes()) {
            throw new IllegalArgumentException("CDR ZIP supera el tamaño comprimido permitido");
        }

        int entries = 0;
        byte[] firstXml = null;
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(zipBytes))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                entries++;
                if (entries > limits.maxEntries()) {
                    throw new IllegalArgumentException("CDR ZIP supera la cantidad de entradas permitida");
                }
                validateEntryName(entry.getName());
                if (firstXml == null && !entry.isDirectory()
                        && entry.getName().toLowerCase(Locale.ROOT).endsWith(".xml")) {
                    firstXml = readBoundedXml(zip, entry, limits, zipBytes.length);
                }
            }
            if (firstXml == null) {
                throw new IllegalArgumentException("El CDR ZIP no contiene XML");
            }
            return firstXml;
        } catch (IOException ex) {
            throw new IllegalArgumentException("CDR ZIP inválido", ex);
        }
    }

    private static byte[] readBoundedXml(ZipInputStream zip,
                                         ZipEntry entry,
                                         Limits limits,
                                         long archiveCompressedBytes) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        long uncompressed = 0;
        int read;
        while ((read = zip.read(buffer)) != -1) {
            uncompressed += read;
            if (uncompressed > limits.maxXmlBytes()) {
                throw new IllegalArgumentException("XML del CDR supera el tamaño descomprimido permitido");
            }
            out.write(buffer, 0, read);
        }

        long compressedSize = entry.getCompressedSize();
        long ratioBase = compressedSize > 0 ? compressedSize : archiveCompressedBytes;
        if (ratioBase > 0 && uncompressed > ratioBase * limits.maxCompressionRatio()) {
            throw new IllegalArgumentException("CDR ZIP excede el ratio de compresión permitido");
        }
        return out.toByteArray();
    }

    private static void validateEntryName(String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("CDR ZIP contiene una entrada sin nombre");
        }
        String normalized = name.replace('\\', '/');
        if (normalized.startsWith("/") || normalized.contains("../") || normalized.equals("..")) {
            throw new IllegalArgumentException("CDR ZIP contiene una ruta no permitida");
        }
    }
}
