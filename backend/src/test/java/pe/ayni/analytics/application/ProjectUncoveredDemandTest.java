package pe.ayni.analytics.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.NoSuchElementException;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import pe.ayni.analytics.domain.model.UnknownUniversity;
import pe.ayni.analytics.infrastructure.UncoveredDemandRepository;
import pe.ayni.identity.IdentityApi;
import pe.ayni.identity.TenantView;
import pe.ayni.shared.events.DemandUncovered;
import pe.ayni.shared.tenancy.TenantContext;

/** What the projection counts of a search that found no hour, without Spring. */
class ProjectUncoveredDemandTest {

  private static final String UPC = "UPC";
  private static final Instant NOW = Instant.parse("2026-10-20T15:00:00Z");

  private final UUID course = UUID.randomUUID();
  private final UncoveredDemandRepository uncoveredDemand = mock(UncoveredDemandRepository.class);
  private final IdentityApi identity = mock(IdentityApi.class);
  private final ProjectUsageUseCase projection =
      new ProjectUsageUseCase(
          null, null, null, uncoveredDemand, identity, Clock.fixed(NOW, ZoneOffset.UTC));

  private DemandUncovered searchedAt(String instant) {
    return new DemandUncovered(
        UPC,
        course,
        Instant.parse("2026-10-22T14:00:00Z"),
        Instant.parse("2026-10-22T18:00:00Z"),
        Instant.parse(instant));
  }

  private boolean project(DemandUncovered event) {
    boolean[] counted = new boolean[1];
    TenantContext.runAs(UPC, () -> counted[0] = projection.demandUncovered(event));
    return counted[0];
  }

  @Test
  void aSearchIsCountedOnTheDayOfTheUniversityNotTheUtcOne() {
    when(identity.requireTenant(UPC))
        .thenReturn(new TenantView(UPC, "UPC", "America/Lima", new BigDecimal("13.00"), true));

    // 22:30 in Lima is already the next day in UTC.
    assertThat(project(searchedAt("2026-10-21T03:30:00Z"))).isTrue();

    verify(uncoveredDemand).countOneMore(UPC, course, LocalDate.of(2026, 10, 20));
  }

  @Test
  void anEventWithoutAMomentIsCountedOnTheDayOfTheClock() {
    when(identity.requireTenant(UPC))
        .thenReturn(new TenantView(UPC, "UPC", "America/Lima", new BigDecimal("13.00"), true));

    assertThat(project(new DemandUncovered(UPC, course, NOW, NOW.plusSeconds(3600), null)))
        .isTrue();

    verify(uncoveredDemand).countOneMore(UPC, course, LocalDate.of(2026, 10, 20));
  }

  @Test
  void aSearchWithoutACourseCountsNothing() {
    assertThat(project(new DemandUncovered(UPC, null, NOW, NOW.plusSeconds(3600), NOW))).isFalse();

    verifyNoInteractions(uncoveredDemand, identity);
  }

  @Test
  void aUniversityWithNoZoneCountsNothing() {
    when(identity.requireTenant(any(String.class))).thenThrow(new NoSuchElementException("unknown"));

    assertThatThrownBy(() -> project(searchedAt("2026-10-20T15:00:00Z")))
        .isInstanceOf(UnknownUniversity.class);

    verifyNoInteractions(uncoveredDemand);
  }
}
