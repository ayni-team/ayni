package pe.ayni.analytics.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import pe.ayni.analytics.domain.model.UnknownUniversity;
import pe.ayni.shared.domain.Credits;
import pe.ayni.shared.events.RecognitionResolved;
import pe.ayni.shared.events.SessionCompleted;
import pe.ayni.shared.events.StudentActivated;
import pe.ayni.shared.tenancy.TenantContext;

/** Projecting is never the reason an operation fails, and it binds the university of the event. */
class UsageEventListenersTest {

  private final ProjectUsageUseCase projection = mock(ProjectUsageUseCase.class);
  private final UsageEventListeners listeners = new UsageEventListeners(projection, new UncoveredDemandQueue());

  private final SessionCompleted completed =
      new SessionCompleted(
          "UPC", UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
          UUID.randomUUID(), Credits.of(2), Instant.parse("2026-10-05T15:00:00Z"));
  private final StudentActivated activated =
      new StudentActivated("UPC", UUID.randomUUID(), "ana@upc.edu.pe", "U2023", Instant.now());
  private final RecognitionResolved resolved =
      new RecognitionResolved("UPC", UUID.randomUUID(), UUID.randomUUID(), true, "ok", Instant.now());

  @Test
  void aFailureToProjectDoesNotReachWhoPublished() {
    when(projection.sessionCompleted(completed)).thenThrow(new IllegalStateException("boom"));
    when(projection.studentActivated(activated)).thenThrow(new IllegalStateException("boom"));
    when(projection.recognitionResolved(resolved)).thenThrow(new IllegalStateException("boom"));

    assertThatCode(() -> listeners.on(completed)).doesNotThrowAnyException();
    assertThatCode(() -> listeners.on(activated)).doesNotThrowAnyException();
    assertThatCode(() -> listeners.on(resolved)).doesNotThrowAnyException();
  }

  @Test
  void aUniversityWithNoZoneToReadIsNotAFault() {
    when(projection.sessionCompleted(completed)).thenThrow(new UnknownUniversity());

    assertThatCode(() -> listeners.on(completed)).doesNotThrowAnyException();
  }

  @Test
  void theUniversityOfTheEventIsBoundWhileProjecting() {
    String[] seen = new String[1];
    when(projection.sessionCompleted(completed))
        .thenAnswer(
            call -> {
              seen[0] = TenantContext.get();
              return true;
            });

    listeners.on(completed);

    verify(projection).sessionCompleted(completed);
    assertThat(seen[0]).isEqualTo("UPC");
    assertThat(TenantContext.get()).isNull();
  }
}
