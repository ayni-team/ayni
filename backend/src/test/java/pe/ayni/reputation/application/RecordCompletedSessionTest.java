package pe.ayni.reputation.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import pe.ayni.reputation.domain.model.RatingWindow;
import pe.ayni.reputation.domain.model.TutorStanding;
import pe.ayni.reputation.infrastructure.RatingWindowRepository;
import pe.ayni.reputation.infrastructure.TutorStandingRepository;
import pe.ayni.shared.tenancy.TenantContext;

class RecordCompletedSessionTest {

    @Test
    void createsStandingAndRatingWindowOnFirstCompletedSession() {
        String tenantId = "upc";
        UUID sessionId = UUID.randomUUID();
        UUID tutorId = UUID.randomUUID();
        UUID studentId = UUID.randomUUID();
        UUID catalogItemId = UUID.randomUUID();
        Instant occurredOn = Instant.parse("2026-09-22T20:00:00Z");

        TutorStandingRepository standings = mock(TutorStandingRepository.class);
        RatingWindowRepository ratingWindows = mock(RatingWindowRepository.class);

        when(
                standings.lockByTenantIdAndTutorIdAndCatalogItemId(
                        tenantId, tutorId, catalogItemId))
                .thenReturn(Optional.empty());

        when(ratingWindows.findByTenantIdAndSessionId(tenantId, sessionId))
                .thenReturn(Optional.empty());

        RecordCompletedSession useCase =
                new RecordCompletedSession(standings, ratingWindows);

        TenantContext.runAs(
                tenantId,
                () ->
                        useCase.record(
                                sessionId,
                                tutorId,
                                studentId,
                                catalogItemId,
                                occurredOn));

        ArgumentCaptor<TutorStanding> standingCaptor =
                ArgumentCaptor.forClass(TutorStanding.class);

        verify(standings).save(standingCaptor.capture());

        TutorStanding savedStanding = standingCaptor.getValue();

        assertEquals(tenantId, savedStanding.tenantId());
        assertEquals(tutorId, savedStanding.tutorId());
        assertEquals(catalogItemId, savedStanding.catalogItemId());
        assertEquals(1, savedStanding.sessionsTaught());
        assertEquals(0, savedStanding.ratingsCount());
        assertNull(savedStanding.averageStars());
        assertEquals(occurredOn, savedStanding.updatedAt());

        ArgumentCaptor<RatingWindow> windowCaptor =
                ArgumentCaptor.forClass(RatingWindow.class);

        verify(ratingWindows).save(windowCaptor.capture());

        RatingWindow savedWindow = windowCaptor.getValue();

        assertEquals(tenantId, savedWindow.tenantId());
        assertEquals(sessionId, savedWindow.sessionId());
        assertEquals(tutorId, savedWindow.tutorId());
        assertEquals(studentId, savedWindow.studentId());
        assertEquals(catalogItemId, savedWindow.catalogItemId());
        assertEquals(occurredOn, savedWindow.openedAt());
    }

    @Test
    void aSessionCompletedTwiceIsCountedOnce() {
        String tenantId = "upc";
        UUID sessionId = UUID.randomUUID();
        UUID tutorId = UUID.randomUUID();
        UUID studentId = UUID.randomUUID();
        UUID catalogItemId = UUID.randomUUID();
        Instant occurredOn = Instant.parse("2026-09-22T20:00:00Z");

        TutorStandingRepository standings = mock(TutorStandingRepository.class);
        RatingWindowRepository ratingWindows = mock(RatingWindowRepository.class);

        when(ratingWindows.findByTenantIdAndSessionId(tenantId, sessionId))
                .thenReturn(
                        Optional.of(
                                new RatingWindow(
                                        tenantId,
                                        sessionId,
                                        tutorId,
                                        studentId,
                                        catalogItemId,
                                        occurredOn)));

        RecordCompletedSession useCase =
                new RecordCompletedSession(standings, ratingWindows);

        TenantContext.runAs(
                tenantId,
                () ->
                        useCase.record(
                                sessionId,
                                tutorId,
                                studentId,
                                catalogItemId,
                                occurredOn));

        verify(standings, never()).save(any());
        verify(ratingWindows, never()).save(any());
    }
}
