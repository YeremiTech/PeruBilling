package pe.com.perubilling.issuer.infrastructure;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import pe.com.perubilling.shared.domain.DocumentType;
import pe.com.perubilling.issuer.domain.DocumentSeriesEntity;

public interface DocumentSeriesRepository extends JpaRepository<DocumentSeriesEntity, UUID> {
    List<DocumentSeriesEntity> findAllByTenantIdAndIssuerIdOrderByDocumentTypeAscSeriesAsc(UUID tenantId, UUID issuerId);
    Optional<DocumentSeriesEntity> findByIdAndTenantIdAndIssuerId(UUID id, UUID tenantId, UUID issuerId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from DocumentSeriesEntity s where s.tenantId=:tenantId and s.issuerId=:issuerId and s.documentType=:documentType and s.series=:series and s.active=true")
    Optional<DocumentSeriesEntity> findForUpdate(@Param("tenantId") UUID tenantId, @Param("issuerId") UUID issuerId,
                                                  @Param("documentType") DocumentType documentType, @Param("series") String series);
}
