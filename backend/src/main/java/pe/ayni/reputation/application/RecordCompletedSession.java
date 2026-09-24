package pe.ayni.reputation.application;

import java.time.Instant;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.ayni.reputation.domain.model.TutorStanding;
import pe.ayni.reputation.infrastructure.TutorStandingRepository;
import pe.ayni.shared.tenancy.TenantContext;

/**
 * Updates the tutor standing projection after a successfully completed session.
 */
@Service
class RecordCompletedSession {

    private final TutorStandingRepository standings;

    RecordCompletedSession(TutorStandingRepository standings) {
        this.standings = standings;
    }

    @Transactional
    void record(UUID tutorId, UUID catalogItemId, Instant occurredOn) {
        String tenantId = TenantContext.require();

        TutorStanding standing =
                standings
                        .findByTenantIdAndTutorIdAndCatalogItemId(
                                tenantId,
                                tutorId,
                                catalogItemId)
                        .orElseGet(
                                () ->
                                        TutorStanding.initial(
                                                tenantId,
                                                tutorId,
                                                catalogItemId,
                                                occurredOn));

        standing.recordCompletedSession(occurredOn);

        standings.save(standing);
    }
}