package pe.ayni.reputation.infrastructure;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
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
}