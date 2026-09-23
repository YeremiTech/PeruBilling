package pe.com.perubilling.identity.infrastructure;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import pe.com.perubilling.identity.domain.UserAccountEntity;

public interface UserAccountRepository extends JpaRepository<UserAccountEntity, UUID> {
    Optional<UserAccountEntity> findByEmailIgnoreCase(String email);
    boolean existsByEmailIgnoreCase(String email);
    Optional<UserAccountEntity> findByIdAndTenantId(UUID id, UUID tenantId);
    List<UserAccountEntity> findAllByTenantIdOrderByEmailAsc(UUID tenantId);
    long countByTenantIdAndEnabledTrueAndRole(UUID tenantId, pe.com.perubilling.identity.domain.UserRole role);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from UserAccountEntity u where lower(u.email)=lower(:email)")
    Optional<UserAccountEntity> findByEmailForAuthentication(@Param("email") String email);
}
