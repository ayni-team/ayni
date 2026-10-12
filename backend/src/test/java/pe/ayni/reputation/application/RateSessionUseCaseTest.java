package pe.ayni.reputation.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;
import pe.ayni.reputation.domain.model.AlreadyRated;
import pe.ayni.reputation.domain.model.InvalidRating;
import pe.ayni.reputation.domain.model.NotAParticipant;
import pe.ayni.reputation.domain.model.Rating;
import pe.ayni.reputation.domain.model.RatingWindow;
import pe.ayni.reputation.domain.model.SessionNotRatable;
import pe.ayni.reputation.domain.model.TutorStanding;
import pe.ayni.reputation.infrastructure.RatingRepository;
import pe.ayni.reputation.infrastructure.RatingWindowRepository;
import pe.ayni.reputation.infrastructure.TutorStandingRepository;
import pe.ayni.shared.events.SessionRated;
import pe.ayni.shared.tenancy.TenantContext;

class RateSessionUseCaseTest {

    @Test
    void studentRatingIsSavedUpdatesStandingAndPublishesEvent() {
        String tenantId = "UPC";

        UUID sessionId = UUID.randomUUID();
        UUID studentId = UUID.randomUUID();
        UUID tutorId = UUID.randomUUID();
        UUID catalogItemId = UUID.randomUUID();

        Instant now = Instant.parse("2026-10-05T22:00:00Z");

        RatingRepository ratings = mock(RatingRepository.class);
        RatingWindowRepository ratingWindows = mock(RatingWindowRepository.class);
        TutorStandingRepository standings = mock(TutorStandingRepository.class);
        ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);

        Clock clock = Clock.fixed(now, ZoneOffset.UTC);

        RatingWindow window =
                new RatingWindow(
                        tenantId,
                        sessionId,
                        tutorId,
                        studentId,
                        catalogItemId,
                        now.minusSeconds(300));

        TutorStanding standing =
                new TutorStanding(
                        tenantId,
                        tutorId,
                        catalogItemId,
                        3,
                        2,
                        new BigDecimal("4.50"),
                        now.minusSeconds(60));

        when(ratingWindows.lockByTenantIdAndSessionId(tenantId, sessionId))
                .thenReturn(Optional.of(window));

        when(
                standings.lockByTenantIdAndTutorIdAndCatalogItemId(
                        tenantId,
                        tutorId,
                        catalogItemId))
                .thenReturn(Optional.of(standing));

        RateSessionUseCase useCase =
                new RateSessionUseCase(
                        ratings,
                        ratingWindows,
                        standings,
                        events,
                        clock);

        TenantContext.runAs(
                tenantId,
                () ->
                        useCase.rate(
                                sessionId,
                                studentId,
                                5,
                                Set.of("CLEAR", "HELPFUL"),
                                null,
                                null,
                                null,
                                "Great session"));

        ArgumentCaptor<Rating> ratingCaptor =
                ArgumentCaptor.forClass(Rating.class);

        verify(ratings).save(ratingCaptor.capture());

        Rating saved = ratingCaptor.getValue();

        assertThat(saved.sessionId()).isEqualTo(sessionId);
        assertThat(saved.ratedBy()).isEqualTo(studentId);
        assertThat(saved.ratedUser()).isEqualTo(tutorId);
        assertThat(saved.direction())
                .isEqualTo(Rating.Direction.STUDENT_TO_TUTOR);
        assertThat(saved.stars()).isEqualTo(5);
        assertThat(saved.tags()).containsExactlyInAnyOrder("CLEAR", "HELPFUL");

        assertThat(standing.ratingsCount()).isEqualTo(3);
        assertThat(standing.averageStars()).isEqualByComparingTo("4.67");

        verify(standings).save(standing);

        ArgumentCaptor<SessionRated> eventCaptor =
                ArgumentCaptor.forClass(SessionRated.class);

        verify(events).publishEvent(eventCaptor.capture());

        SessionRated event = eventCaptor.getValue();

        assertThat(event.tenantId()).isEqualTo(tenantId);
        assertThat(event.sessionId()).isEqualTo(sessionId);
        assertThat(event.ratedUserId()).isEqualTo(tutorId);
        assertThat(event.catalogItemId()).isEqualTo(catalogItemId);
        assertThat(event.direction())
                .isEqualTo(SessionRated.Direction.STUDENT_TO_TUTOR);
        assertThat(event.stars()).isEqualTo(5);
        assertThat(event.occurredOn()).isEqualTo(now);
    }

    private static final String TENANT = "UPC";
    private static final Instant NOW = Instant.parse("2026-10-05T22:00:00Z");

    private final UUID sessionId = UUID.randomUUID();
    private final UUID studentId = UUID.randomUUID();
    private final UUID tutorId = UUID.randomUUID();
    private final UUID catalogItemId = UUID.randomUUID();

    private RatingRepository ratings;
    private RatingWindowRepository ratingWindows;
    private TutorStandingRepository standings;
    private ApplicationEventPublisher events;
    private RateSessionUseCase useCase;

    @BeforeEach
    void anOpenWindowAndATutorWithStanding() {
        ratings = mock(RatingRepository.class);
        ratingWindows = mock(RatingWindowRepository.class);
        standings = mock(TutorStandingRepository.class);
        events = mock(ApplicationEventPublisher.class);
        useCase =
                new RateSessionUseCase(
                        ratings,
                        ratingWindows,
                        standings,
                        events,
                        Clock.fixed(NOW, ZoneOffset.UTC));

        when(ratingWindows.lockByTenantIdAndSessionId(TENANT, sessionId))
                .thenReturn(
                        Optional.of(
                                new RatingWindow(
                                        TENANT,
                                        sessionId,
                                        tutorId,
                                        studentId,
                                        catalogItemId,
                                        NOW.minusSeconds(300))));
        when(
                standings.lockByTenantIdAndTutorIdAndCatalogItemId(
                        TENANT, tutorId, catalogItemId))
                .thenReturn(
                        Optional.of(
                                new TutorStanding(
                                        TENANT, tutorId, catalogItemId, 1, 0, null, NOW)));
    }

    private void rateAs(
            UUID userId,
            Integer stars,
            Boolean wasPunctual,
            Boolean connectionOk,
            Boolean sessionFlowed) {
        TenantContext.runAs(
                TENANT,
                () ->
                        useCase.rate(
                                sessionId,
                                userId,
                                stars,
                                Set.of(),
                                wasPunctual,
                                connectionOk,
                                sessionFlowed,
                                null));
    }

    private void assertNothingWasRecorded() {
        verify(ratings, never()).save(any());
        verify(standings, never()).save(any());
        verify(events, never()).publishEvent(any(Object.class));
    }

    @Test
    void aStrangerIsNotAParticipant() {
        assertThatThrownBy(() -> rateAs(UUID.randomUUID(), 5, null, null, null))
                .isInstanceOf(NotAParticipant.class);
        assertNothingWasRecorded();
    }

    @Test
    void aSessionWithoutWindowIsNotRatable() {
        when(ratingWindows.lockByTenantIdAndSessionId(TENANT, sessionId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> rateAs(studentId, 5, null, null, null))
                .isInstanceOf(SessionNotRatable.class);
        assertNothingWasRecorded();
    }

    @Test
    void aDirectionAlreadyRatedIsRefused() {
        when(
                ratings.existsByTenantIdAndSessionIdAndDirection(
                        TENANT, sessionId, Rating.Direction.STUDENT_TO_TUTOR))
                .thenReturn(true);

        assertThatThrownBy(() -> rateAs(studentId, 4, null, null, null))
                .isInstanceOf(AlreadyRated.class);
        assertNothingWasRecorded();
    }

    @Test
    void theTutorMayRateEvenWhenTheStudentAlreadyDid() {
        when(
                ratings.existsByTenantIdAndSessionIdAndDirection(
                        TENANT, sessionId, Rating.Direction.STUDENT_TO_TUTOR))
                .thenReturn(true);

        rateAs(tutorId, null, true, true, false);

        ArgumentCaptor<Rating> saved = ArgumentCaptor.forClass(Rating.class);
        verify(ratings).save(saved.capture());
        assertThat(saved.getValue().direction())
                .isEqualTo(Rating.Direction.TUTOR_TO_STUDENT);
        assertThat(saved.getValue().wasPunctual()).isTrue();
        assertThat(saved.getValue().sessionFlowed()).isFalse();
        verify(standings, never()).save(any());
    }

    @Test
    void aStudentWithoutStarsIsRefused() {
        assertThatThrownBy(() -> rateAs(studentId, null, null, null, null))
                .isInstanceOf(InvalidRating.class);
        assertNothingWasRecorded();
    }

    @Test
    void aTutorMissingOneOfTheThreeFactsIsRefused() {
        assertThatThrownBy(() -> rateAs(tutorId, null, true, null, true))
                .isInstanceOf(InvalidRating.class);
        assertNothingWasRecorded();
    }
}
