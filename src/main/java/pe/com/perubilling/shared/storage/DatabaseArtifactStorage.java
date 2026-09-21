package pe.com.perubilling.shared.storage;

import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "app.artifacts", name = "backend", havingValue = "database")
public class DatabaseArtifactStorage implements ArtifactStorage {
    private final JdbcClient jdbc;
    private final long maxReadBytes;

    public DatabaseArtifactStorage(
            JdbcClient jdbc,
            @Value("${app.artifacts.max-read-bytes:52428800}") long maxReadBytes) {
        this.jdbc = jdbc;
        this.maxReadBytes = Math.max(1024L, maxReadBytes);
    }

    @Override
    public String store(UUID tenantId, UUID ownerId, String fileName, byte[] content) {
        if (tenantId == null || ownerId == null) throw new IllegalArgumentException("tenantId y ownerId son obligatorios");
        if (content == null) throw new IllegalArgumentException("content es obligatorio");
        if (content.length > maxReadBytes) throw new IllegalStateException("Artefacto excede el tamaño máximo configurado");
        String safeName = Path.of(fileName).getFileName().toString();
        if (safeName.isBlank()) throw new IllegalArgumentException("Nombre de artefacto inválido");
        String path = tenantId + "/" + ownerId + "/" + safeName;
        String checksum = sha256(content);
        Instant now = Instant.now();

        jdbc.sql("""
                INSERT INTO artifact_blob
                    (id, tenant_id, owner_id, path, content, content_length, sha256, created_at, updated_at)
                VALUES
                    (:id, :tenantId, :ownerId, :path, :content, :length, :sha256, :now, :now)
                ON CONFLICT (path) DO UPDATE SET
                    tenant_id = EXCLUDED.tenant_id,
                    owner_id = EXCLUDED.owner_id,
                    content = EXCLUDED.content,
                    content_length = EXCLUDED.content_length,
                    sha256 = EXCLUDED.sha256,
                    updated_at = EXCLUDED.updated_at
                """)
                .param("id", UUID.randomUUID())
                .param("tenantId", tenantId)
                .param("ownerId", ownerId)
                .param("path", path)
                .param("content", content)
                .param("length", (long) content.length)
                .param("sha256", checksum)
                .param("now", now)
                .update();
        return path;
    }

    @Override
    public byte[] read(String path) {
        ArtifactRow row = jdbc.sql("SELECT content, content_length, sha256 FROM artifact_blob WHERE path = :path")
                .param("path", path)
                .query((rs, rowNum) -> new ArtifactRow(
                        rs.getBytes("content"),
                        rs.getLong("content_length"),
                        rs.getString("sha256")))
                .single();
        byte[] content = row.content();
        long length = row.length();
        if (length > maxReadBytes || content.length > maxReadBytes) {
            throw new IllegalStateException("Artefacto excede el tamaño máximo de lectura");
        }
        if (length != content.length) throw new IllegalStateException("Longitud de artefacto inconsistente");
        String expected = row.sha256();
        String actual = sha256(content);
        if (!MessageDigest.isEqual(expected.getBytes(java.nio.charset.StandardCharsets.US_ASCII),
                actual.getBytes(java.nio.charset.StandardCharsets.US_ASCII))) {
            throw new IllegalStateException("Checksum inválido para artefacto: " + path);
        }
        return content;
    }

    public boolean ping() {
        return Boolean.TRUE.equals(jdbc.sql("SELECT TRUE").query(Boolean.class).single());
    }

    private record ArtifactRow(byte[] content, long length, String sha256) {}

    private String sha256(byte[] content) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
        } catch (Exception ex) {
            throw new IllegalStateException("No se pudo calcular SHA-256", ex);
        }
    }
}
