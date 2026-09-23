package pe.com.perubilling.issuer.infrastructure;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import pe.com.perubilling.issuer.domain.DigitalCertificateEntity;

public interface DigitalCertificateRepository extends JpaRepository<DigitalCertificateEntity, UUID> {
    List<DigitalCertificateEntity> findAllByTenantIdAndIssuerIdOrderByCreatedAtDesc(
            UUID tenantId, UUID issuerId);

    Optional<DigitalCertificateEntity>
            findFirstByTenantIdAndIssuerIdAndActiveTrueAndValidFromBeforeAndValidUntilAfterOrderByValidUntilDesc(
                    UUID tenantId, UUID issuerId, Instant now1, Instant now2);

    Optional<DigitalCertificateEntity> findByIdAndTenantIdAndIssuerId(
            UUID id, UUID tenantId, UUID issuerId);

    boolean existsByTenantIdAndIssuerIdAndFingerprint(
            UUID tenantId, UUID issuerId, String fingerprint);
}
