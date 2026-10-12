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
                        .lockByTenantIdAndTutorIdAndCatalogItemId(
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

        // The window is opened once per session, in the same transaction that counts it, so its
        // presence says the session was already counted: delivering the event again changes
        // nothing. It is read after the standing is locked, so two deliveries at once are told
        // apart too.
        if (ratingWindows
                .findByTenantIdAndSessionId(tenantId, sessionId)
                .isPresent()) {
            return;
        }

        standing.recordCompletedSession(occurredOn);
        standings.save(standing);

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
