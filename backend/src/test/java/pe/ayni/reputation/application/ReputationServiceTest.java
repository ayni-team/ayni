package pe.ayni.reputation.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import pe.ayni.reputation.TutorStandingView;
import pe.ayni.reputation.domain.model.TutorStanding;
import pe.ayni.reputation.infrastructure.TutorStandingRepository;
import pe.ayni.shared.tenancy.TenantContext;

class ReputationServiceTest {

    @Test
    void hidesAverageWhenTutorHasFewerThanThreeRatings() {
        String tenantId = "upc";
        UUID tutorId = UUID.randomUUID();
        UUID catalogItemId = UUID.randomUUID();

        TutorStandingRepository standings = mock(TutorStandingRepository.class);

        TutorStanding standing =
                new TutorStanding(
                        tenantId,
                        tutorId,
                        catalogItemId,
                        5,
                        2,
                        new BigDecimal("4.50"),
                        Instant.now());

        when(
                standings.findByTenantIdAndTutorIdAndCatalogItemId(
                        tenantId, tutorId, catalogItemId))
                .thenReturn(Optional.of(standing));

        ReputationService service = new ReputationService(standings);

        TenantContext.runAs(
                tenantId,
                () -> {
                    TutorStandingView view =
                            service.standingOf(tutorId, catalogItemId).orElseThrow();

                    assertEquals(2, view.ratingsCount());
                    assertNull(view.averageStars());
                });
    }

    @Test
    void showsAverageWhenTutorHasThreeRatings() {
        String tenantId = "upc";
        UUID tutorId = UUID.randomUUID();
        UUID catalogItemId = UUID.randomUUID();

        TutorStandingRepository standings = mock(TutorStandingRepository.class);

        TutorStanding standing =
                new TutorStanding(
                        tenantId,
                        tutorId,
                        catalogItemId,
                        5,
                        3,
                        new BigDecimal("4.67"),
                        Instant.now());

        when(
                standings.findByTenantIdAndTutorIdAndCatalogItemId(
                        tenantId, tutorId, catalogItemId))
                .thenReturn(Optional.of(standing));

        ReputationService service = new ReputationService(standings);

        TenantContext.runAs(
                tenantId,
                () -> {
                    TutorStandingView view =
                            service.standingOf(tutorId, catalogItemId).orElseThrow();

                    assertEquals(3, view.ratingsCount());
                    assertEquals(new BigDecimal("4.67"), view.averageStars());
                });
    }
}
