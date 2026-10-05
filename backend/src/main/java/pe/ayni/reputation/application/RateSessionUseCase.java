package pe.ayni.reputation.application;

import java.time.Clock;
import java.time.Instant;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.UUID;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.ayni.reputation.domain.model.Rating;
import pe.ayni.reputation.domain.model.RatingWindow;
import pe.ayni.reputation.domain.model.TutorStanding;
import pe.ayni.reputation.infrastructure.RatingRepository;
import pe.ayni.reputation.infrastructure.RatingWindowRepository;
import pe.ayni.reputation.infrastructure.TutorStandingRepository;
import pe.ayni.shared.events.SessionRated;
import pe.ayni.shared.tenancy.TenantContext;

@Service
public class RateSessionUseCase {

    private final RatingRepository ratings;
    private final RatingWindowRepository ratingWindows;
    private final TutorStandingRepository standings;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    RateSessionUseCase(
            RatingRepository ratings,
            RatingWindowRepository ratingWindows,
            TutorStandingRepository standings,
            ApplicationEventPublisher events,
            Clock clock) {

        this.ratings = ratings;
        this.ratingWindows = ratingWindows;
        this.standings = standings;
        this.events = events;
        this.clock = clock;
    }

    @Transactional
    public UUID rate(
            UUID sessionId,
            UUID userId,
            Integer stars,
            Set<String> tags,
            Boolean wasPunctual,
            Boolean connectionOk,
            Boolean sessionFlowed,
            String comment) {

        String tenantId = TenantContext.require();

        RatingWindow window =
                ratingWindows
                        .findByTenantIdAndSessionId(tenantId, sessionId)
                        .orElseThrow(
                                () ->
                                        new NoSuchElementException(
                                                "The session is not available for rating"));

        if (!window.isParticipant(userId)) {
            throw new IllegalArgumentException(
                    "Only participants of the session may submit a rating");
        }

        Instant now = clock.instant();

        Rating rating;

        if (userId.equals(window.studentId())) {
            rating =
                    studentRating(
                            tenantId,
                            window,
                            userId,
                            stars,
                            tags,
                            comment,
                            now);
        } else {
            rating =
                    tutorRating(
                            tenantId,
                            window,
                            userId,
                            wasPunctual,
                            connectionOk,
                            sessionFlowed,
                            comment,
                            now);
        }

        ratings.save(rating);

        events.publishEvent(
                new SessionRated(
                        tenantId,
                        sessionId,
                        rating.ratedUser(),
                        window.catalogItemId(),
                        toEventDirection(rating.direction()),
                        rating.stars(),
                        now));

        return rating.id();
    }

    private Rating studentRating(
            String tenantId,
            RatingWindow window,
            UUID studentId,
            Integer stars,
            Set<String> tags,
            String comment,
            Instant now) {

        if (stars == null) {
            throw new IllegalArgumentException(
                    "A student rating requires stars");
        }

        ensureNotAlreadyRated(
                tenantId,
                window.sessionId(),
                Rating.Direction.STUDENT_TO_TUTOR);

        Rating rating =
                Rating.studentToTutor(
                        UUID.randomUUID(),
                        tenantId,
                        window.sessionId(),
                        studentId,
                        window.tutorId(),
                        stars,
                        tags,
                        comment,
                        now);

        TutorStanding standing =
                standings
                        .findByTenantIdAndTutorIdAndCatalogItemId(
                                tenantId,
                                window.tutorId(),
                                window.catalogItemId())
                        .orElseThrow(
                                () ->
                                        new IllegalStateException(
                                                "Tutor standing was not opened for the completed session"));

        standing.recordRating(stars, now);
        standings.save(standing);

        return rating;
    }

    private Rating tutorRating(
            String tenantId,
            RatingWindow window,
            UUID tutorId,
            Boolean wasPunctual,
            Boolean connectionOk,
            Boolean sessionFlowed,
            String comment,
            Instant now) {

        if (wasPunctual == null
                || connectionOk == null
                || sessionFlowed == null) {
            throw new IllegalArgumentException(
                    "A tutor rating requires punctuality, connection and session flow");
        }

        ensureNotAlreadyRated(
                tenantId,
                window.sessionId(),
                Rating.Direction.TUTOR_TO_STUDENT);

        return Rating.tutorToStudent(
                UUID.randomUUID(),
                tenantId,
                window.sessionId(),
                tutorId,
                window.studentId(),
                wasPunctual,
                connectionOk,
                sessionFlowed,
                comment,
                now);
    }

    private void ensureNotAlreadyRated(
            String tenantId,
            UUID sessionId,
            Rating.Direction direction) {

        if (ratings.existsByTenantIdAndSessionIdAndDirection(
                tenantId,
                sessionId,
                direction)) {

            throw new IllegalStateException(
                    "This participant already rated the session");
        }
    }

    private SessionRated.Direction toEventDirection(
            Rating.Direction direction) {

        return switch (direction) {
            case STUDENT_TO_TUTOR ->
                    SessionRated.Direction.STUDENT_TO_TUTOR;
            case TUTOR_TO_STUDENT ->
                    SessionRated.Direction.TUTOR_TO_STUDENT;
        };
    }
}