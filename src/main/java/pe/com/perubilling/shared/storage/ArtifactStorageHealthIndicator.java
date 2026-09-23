package pe.com.perubilling.shared.storage;

import java.nio.file.Files;
import java.nio.file.Path;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.stereotype.Component;

@Component("artifactStorage")
public class ArtifactStorageHealthIndicator implements HealthIndicator {
    private final ArtifactStorage storage;

    public ArtifactStorageHealthIndicator(ArtifactStorage storage) {
        this.storage = storage;
    }

    @Override
    public Health health() {
        try {
            if (storage instanceof FileSystemArtifactStorage fileSystem) {
                Path root = fileSystem.rootPath();
                Files.createDirectories(root);
                boolean writable = Files.isDirectory(root) && Files.isWritable(root);
                return writable
                        ? Health.up().withDetail("backend", "filesystem").withDetail("root", root.toString()).build()
                        : Health.down().withDetail("backend", "filesystem").withDetail("root", root.toString()).build();
            }
            if (storage instanceof DatabaseArtifactStorage database) {
                return database.ping()
                        ? Health.up().withDetail("backend", "database").build()
                        : Health.down().withDetail("backend", "database").build();
            }
            return Health.unknown().withDetail("backend", storage.getClass().getSimpleName()).build();
        } catch (Exception ex) {
            return Health.down(ex).build();
        }
    }
}
