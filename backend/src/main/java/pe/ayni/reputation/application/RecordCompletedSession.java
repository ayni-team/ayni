package pe.ayni.reputation.application;

import java.time.Instant;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.ayni.reputation.domain.model.RatingWindow;
import pe.ayni.reputation.domain.model.TutorStanding;
import pe.ayni.reputation.infrastructure.RatingWindowRepository;
import pe.ayni.reputation.infrastructure.TutorStandingRepository;
import pe.ayni.shared.tenancy.TenantContext;

/**
 * Updates reputation after a successfully completed session.
 *
 * <p>The completed session counts towards the tutor standing and opens the rating window for both
 * participants.
 */
@Service
class RecordCompletedSession {

    private final TutorStandingRepository standings;
    private final RatingWindowRepository ratingWindows;

    RecordCompletedSession(
            TutorStandingRepository standings,
            RatingWindowRepository ratingWindows) {
        this.standings = standings;
        this.ratingWindows = ratingWindows;
    }

    @Transactional
    void record(
            UUID sessionId,
            UUID tutorId,
            UUID studentId,
            UUID catalogItemId,
            Instant occurredOn) {

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

        if (ratingWindows
                .findByTenantIdAndSessionId(tenantId, sessionId)
                .isEmpty()) {

            ratingWindows.save(
                    new RatingWindow(
                            tenantId,
                            sessionId,
                            tutorId,
                            studentId,
                            catalogItemId,
                            occurredOn));
        }
    }
}