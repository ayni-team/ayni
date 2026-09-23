package pe.ayni.reputation.application;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.ayni.reputation.ReputationApi;
import pe.ayni.reputation.TutorStandingView;
import pe.ayni.reputation.infrastructure.TutorStandingRepository;
import pe.ayni.shared.tenancy.TenantContext;

/**
 * Provides the reputation information exposed to the other modules.
 */
@Service
public class ReputationService implements ReputationApi {

    private final TutorStandingRepository standings;

    ReputationService(TutorStandingRepository standings) {
        this.standings = standings;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<TutorStandingView> standingOf(UUID tutorId, UUID catalogItemId) {
        Objects.requireNonNull(tutorId, "tutorId must not be null");
        Objects.requireNonNull(catalogItemId, "catalogItemId must not be null");

        String tenantId = TenantContext.require();

        return standings
                .findByTenantIdAndTutorIdAndCatalogItemId(tenantId, tutorId, catalogItemId)
                .map(
                        standing ->
                                new TutorStandingView(
                                        standing.tutorId(),
                                        standing.catalogItemId(),
                                        standing.sessionsTaught(),
                                        standing.ratingsCount(),
                                        standing.averageStars()));
    }
}