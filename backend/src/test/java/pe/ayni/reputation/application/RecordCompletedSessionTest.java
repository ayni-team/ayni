package pe.ayni.reputation.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import pe.ayni.reputation.domain.model.TutorStanding;
import pe.ayni.reputation.infrastructure.TutorStandingRepository;
import pe.ayni.shared.tenancy.TenantContext;

class RecordCompletedSessionTest {

    @Test
    void createsStandingOnFirstCompletedSession() {
        String tenantId = "upc";
        UUID tutorId = UUID.randomUUID();
        UUID catalogItemId = UUID.randomUUID();
        Instant occurredOn = Instant.parse("2026-09-22T20:00:00Z");

        TutorStandingRepository standings = mock(TutorStandingRepository.class);

        when(
                standings.findByTenantIdAndTutorIdAndCatalogItemId(
                        tenantId, tutorId, catalogItemId))
                .thenReturn(Optional.empty());

        RecordCompletedSession useCase = new RecordCompletedSession(standings);

        TenantContext.runAs(
                tenantId,
                () -> useCase.record(tutorId, catalogItemId, occurredOn));

        ArgumentCaptor<TutorStanding> captor =
                ArgumentCaptor.forClass(TutorStanding.class);

        verify(standings).save(captor.capture());

        TutorStanding saved = captor.getValue();

        assertEquals(tenantId, saved.tenantId());
        assertEquals(tutorId, saved.tutorId());
        assertEquals(catalogItemId, saved.catalogItemId());
        assertEquals(1, saved.sessionsTaught());
        assertEquals(0, saved.ratingsCount());
        assertNull(saved.averageStars());
        assertEquals(occurredOn, saved.updatedAt());
    }
}