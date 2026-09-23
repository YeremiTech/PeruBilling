package pe.com.perubilling.billing.infrastructure;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import pe.com.perubilling.billing.domain.DocumentStatus;
import pe.com.perubilling.shared.domain.DocumentType;
import pe.com.perubilling.billing.domain.ElectronicDocumentEntity;

public interface ElectronicDocumentRepository extends JpaRepository<ElectronicDocumentEntity, UUID> {
    Optional<ElectronicDocumentEntity> findByIdAndTenantId(UUID id, UUID tenantId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select d from ElectronicDocumentEntity d where d.id = :id and d.tenantId = :tenantId")
    Optional<ElectronicDocumentEntity> findByIdAndTenantIdForUpdate(
            @Param("id") UUID id, @Param("tenantId") UUID tenantId);
    Optional<ElectronicDocumentEntity> findByTenantIdAndIssuerIdAndDocumentTypeAndFullNumber(
            UUID tenantId, UUID issuerId, DocumentType documentType, String fullNumber);
    Optional<ElectronicDocumentEntity> findByTenantIdAndIssuerIdAndExternalId(
            UUID tenantId, UUID issuerId, String externalId);

    @Query("""
            select d from ElectronicDocumentEntity d
            where d.tenantId = :tenantId
              and (:issuerId is null or d.issuerId = :issuerId)
              and (:documentType is null or d.documentType = :documentType)
              and (:operationType is null or d.operationType = :operationType)
              and (:series is null or d.series = :series)
              and (:number is null or d.fullNumber = :number)
              and (:externalId is null or d.externalId = :externalId)
              and (:status is null or d.status = :status)
              and (:customerDocumentNumber is null or d.customerDocumentNumber = :customerDocumentNumber)
              and (:fromIssueDate is null or d.issueDate >= :fromIssueDate)
              and (:toIssueDate is null or d.issueDate <= :toIssueDate)
            order by d.createdAt desc
            """)
    Page<ElectronicDocumentEntity> search(
            @Param("tenantId") UUID tenantId,
            @Param("issuerId") UUID issuerId,
            @Param("documentType") DocumentType documentType,
            @Param("operationType") String operationType,
            @Param("series") String series,
            @Param("number") String number,
            @Param("externalId") String externalId,
            @Param("status") DocumentStatus status,
            @Param("customerDocumentNumber") String customerDocumentNumber,
            @Param("fromIssueDate") LocalDate fromIssueDate,
            @Param("toIssueDate") LocalDate toIssueDate,
            Pageable pageable);

    List<ElectronicDocumentEntity> findAllByTenantIdAndIssuerIdAndIssueDateAndStatusOrderByCreatedAtAsc(
            UUID tenantId, UUID issuerId, LocalDate issueDate, DocumentStatus status);
    List<ElectronicDocumentEntity> findAllByDailySummaryIdOrderByCreatedAtAsc(UUID dailySummaryId);
}
