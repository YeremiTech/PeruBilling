package pe.com.perubilling.access.infrastructure;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;
import pe.com.perubilling.access.domain.ApiKeyEntity;

public interface ApiKeyRepository extends JpaRepository<ApiKeyEntity, UUID> {
    Optional<ApiKeyEntity> findBySecretHashAndRevokedAtIsNull(String secretHash);

    List<ApiKeyEntity> findAllByTenantIdOrderByCreatedAtDesc(UUID tenantId);

    Optional<ApiKeyEntity> findByIdAndTenantId(UUID id, UUID tenantId);

    @Modifying(clearAutomatically = false, flushAutomatically = false)
    @Transactional
    @Query("""
            update ApiKeyEntity k
               set k.lastUsedAt = :now
             where k.id = :id
               and (k.lastUsedAt is null or k.lastUsedAt < :threshold)
            """)
    int touchLastUsedAt(
            @Param("id") UUID id,
            @Param("now") Instant now,
            @Param("threshold") Instant threshold);
}
