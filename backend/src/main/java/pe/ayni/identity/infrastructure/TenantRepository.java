package pe.ayni.identity.infrastructure;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import pe.ayni.identity.domain.model.Tenant;
import pe.ayni.identity.domain.model.TenantStatus;

/**
 * Universities registered in Ayni.
 *
 * <p>Tenants are not tenant-scoped themselves: they define the universities available in the
 * platform.
 */
public interface TenantRepository extends JpaRepository<Tenant, UUID> {

    Optional<Tenant> findByCode(String code);

    /**
     * The university, locked until the end of the transaction: whoever changes what hangs from it
     * and must read it first (its credit policy) waits here for the one before.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from Tenant t where t.code = :code")
    Optional<Tenant> lockByCode(@Param("code") String code);

    Optional<Tenant> findByCodeAndStatus(String code, TenantStatus status);

    List<Tenant> findByStatus(TenantStatus status);

    /**
     * Waits for its turn to register a university, until the transaction ends.
     *
     * <p>The code is unique in the table, but the email domains are an array nothing constrains, and
     * the check for either happens before the insert. Whoever registers takes this lock first and
     * then looks, so the second request finds what the first one committed. The lock is held by the
     * database, so it also holds across several instances of the application.
     *
     * @return nothing useful: only the wait matters
     */
    @Query(value = "select cast(pg_advisory_xact_lock(7010001) as text)", nativeQuery = true)
    String lockUniversityRegistration();
}
