package pe.com.perubilling.shared.storage;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

@Component
@ConditionalOnProperty(prefix = "app.artifacts", name = "backend", havingValue = "filesystem", matchIfMissing = true)
public class FileSystemArtifactStorage implements ArtifactStorage {
    private final Path root;
    private final long maxReadBytes;
    private final boolean requireChecksum;

    public FileSystemArtifactStorage(
            @Value("${app.artifacts.root}") String root,
            @Value("${app.artifacts.max-read-bytes:52428800}") long maxReadBytes,
            @Value("${app.artifacts.require-checksum:false}") boolean requireChecksum) {
        this.root = Path.of(root).toAbsolutePath().normalize();
        this.maxReadBytes = Math.max(1024L, maxReadBytes);
        this.requireChecksum = requireChecksum;
    }

    @Override
    public String store(UUID tenantId, UUID documentId, String fileName, byte[] content) {
        try {
            String safeName = Path.of(fileName).getFileName().toString();
            Path dir = root.resolve(tenantId.toString()).resolve(documentId.toString()).normalize();
            if (!dir.startsWith(root)) throw new SecurityException("Ruta de almacenamiento inválida");
            Files.createDirectories(dir);
            Path file = dir.resolve(safeName).normalize();
            if (!file.startsWith(dir)) throw new SecurityException("Nombre de archivo inválido");

            Path temp = Files.createTempFile(dir, ".artifact-", ".tmp");
            Files.write(temp, content, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
            moveAtomically(temp, file);

            String checksum = sha256(content);
            Path checksumFile = checksumPath(file);
            Path checksumTemp = Files.createTempFile(dir, ".checksum-", ".tmp");
            Files.writeString(checksumTemp, checksum + "  " + safeName + System.lineSeparator(),
                    StandardCharsets.US_ASCII, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
            moveAtomically(checksumTemp, checksumFile);

            return root.relativize(file).toString().replace('\\', '/');
        } catch (IOException ex) {
            throw new IllegalStateException("No se pudo almacenar el artefacto", ex);
        }
    }

    @Override
    public byte[] read(String path) {
        try {
            Path file = resolveSafe(path);
            if (!Files.isRegularFile(file)) throw new IllegalStateException("Artefacto inexistente");
            long size = Files.size(file);
            if (size > maxReadBytes) throw new IllegalStateException("Artefacto excede el tamaño máximo de lectura");
            byte[] content = Files.readAllBytes(file);
            verifyChecksum(file, content);
            return content;
        } catch (IOException ex) {
            throw new IllegalStateException("No se pudo leer el artefacto", ex);
        }
    }

    public Path rootPath() {
        return root;
    }

    private Path resolveSafe(String path) {
        Path file = root.resolve(path).normalize();
        if (!file.startsWith(root)) throw new SecurityException("Ruta de almacenamiento inválida");
        return file;
    }

    private void verifyChecksum(Path file, byte[] content) throws IOException {
        Path checksumFile = checksumPath(file);
        if (!Files.isRegularFile(checksumFile)) {
            if (requireChecksum) throw new IllegalStateException("Artefacto sin checksum: " + root.relativize(file));
            return;
        }
        String line = Files.readString(checksumFile, StandardCharsets.US_ASCII).trim();
        String expected = line.split("\\s+", 2)[0].toLowerCase();
        String actual = sha256(content);
        if (!MessageDigest.isEqual(expected.getBytes(StandardCharsets.US_ASCII), actual.getBytes(StandardCharsets.US_ASCII))) {
            throw new IllegalStateException("Checksum inválido para artefacto: " + root.relativize(file));
        }
    }

    private Path checksumPath(Path file) {
        return file.resolveSibling(file.getFileName() + ".sha256");
    }

    private String sha256(byte[] content) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
        } catch (Exception ex) {
            throw new IllegalStateException("No se pudo calcular SHA-256", ex);
        }
    }

    private void moveAtomically(Path source, Path target) throws IOException {
        try {
            Files.move(source, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException ex) {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
