package pe.ayni.reputation.infrastructure;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import pe.ayni.reputation.domain.model.TutorStanding;
import pe.ayni.reputation.domain.model.TutorStandingId;

/**
 * Tutor standing projections.
 *
 * <p>Every query includes the university so reputation data never crosses tenant boundaries.
 */
public interface TutorStandingRepository
        extends JpaRepository<TutorStanding, TutorStandingId> {

    Optional<TutorStanding> findByTenantIdAndTutorIdAndCatalogItemId(
            String tenantId, UUID tutorId, UUID catalogItemId);

    /**
     * A tutor's standing in one skill, locked for writing until the transaction ends. The count and
     * the average are read and written back, so whoever changes them takes this lock first: two
     * students rating the same tutor at once would otherwise both start from the same figures and
     * the second write would erase the first rating.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query(
            "select standing from TutorStanding standing"
                    + " where standing.tenantId = :tenantId"
                    + " and standing.tutorId = :tutorId"
                    + " and standing.catalogItemId = :catalogItemId")
    Optional<TutorStanding> lockByTenantIdAndTutorIdAndCatalogItemId(
            @Param("tenantId") String tenantId,
            @Param("tutorId") UUID tutorId,
            @Param("catalogItemId") UUID catalogItemId);
}