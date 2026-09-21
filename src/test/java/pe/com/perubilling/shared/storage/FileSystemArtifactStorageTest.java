package pe.com.perubilling.shared.storage;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FileSystemArtifactStorageTest {
    @TempDir Path temp;

    @Test
    void storesChecksumAndRejectsTampering() throws Exception {
        var storage = new FileSystemArtifactStorage(temp.toString(), 1024 * 1024, true);
        UUID tenant = UUID.randomUUID();
        UUID document = UUID.randomUUID();
        byte[] original = "contenido-cpe".getBytes(java.nio.charset.StandardCharsets.UTF_8);

        String relative = storage.store(tenant, document, "invoice.xml", original);
        assertArrayEquals(original, storage.read(relative));
        assertTrue(Files.exists(temp.resolve(relative + ".sha256")));

        Files.writeString(temp.resolve(relative), "alterado");
        assertThrows(IllegalStateException.class, () -> storage.read(relative));
    }

    @Test
    void refusesTraversalAndOversizedRead() throws Exception {
        var storage = new FileSystemArtifactStorage(temp.toString(), 4, false);
        assertThrows(SecurityException.class, () -> storage.read("../../etc/passwd"));
        String relative = storage.store(UUID.randomUUID(), UUID.randomUUID(), "x.bin", new byte[]{1,2,3,4,5});
        assertThrows(IllegalStateException.class, () -> storage.read(relative));
    }
}
