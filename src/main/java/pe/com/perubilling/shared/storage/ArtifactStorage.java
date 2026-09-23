package pe.com.perubilling.shared.storage;

import java.util.UUID;

public interface ArtifactStorage {
    String store(UUID tenantId, UUID documentId, String fileName, byte[] content);
    byte[] read(String path);
}
