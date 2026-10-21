package pe.ayni.analytics.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import pe.ayni.analytics.domain.model.UnknownUniversity;
import pe.ayni.shared.domain.Credits;
import pe.ayni.shared.events.DemandUncovered;
import pe.ayni.shared.events.RecognitionResolved;
import pe.ayni.shared.events.SessionCompleted;
import pe.ayni.shared.events.StudentActivated;
import pe.ayni.shared.tenancy.TenantContext;

/** Projecting is never the reason an operation fails, and it binds the university of the event. */
class UsageEventListenersTest {

  private final ProjectUsageUseCase projection = mock(ProjectUsageUseCase.class);
  private final UncoveredDemandQueue queue = new UncoveredDemandQueue();
  private final UsageEventListeners listeners = new UsageEventListeners(projection, queue);

  private final SessionCompleted completed =
      new SessionCompleted(
          "UPC", UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
          UUID.randomUUID(), Credits.of(2), Instant.parse("2026-10-05T15:00:00Z"));
  private final StudentActivated activated =
      new StudentActivated("UPC", UUID.randomUUID(), "ana@upc.edu.pe", "U2023", Instant.now());
  private final DemandUncovered uncovered =
      new DemandUncovered(
          "UPC",
          UUID.randomUUID(),
          Instant.parse("2026-10-06T14:00:00Z"),
          Instant.parse("2026-10-06T18:00:00Z"),
          Instant.parse("2026-10-05T15:00:00Z"));
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

  @AfterEach
  void stopTheWorkers() {
    queue.stop();
  }

  @Test
  void anUncoveredSearchIsCountedAwayFromTheThreadThatPublishedIt() throws InterruptedException {
    String[] seenUniversity = new String[1];
    Thread[] countedBy = new Thread[1];
    when(projection.demandUncovered(uncovered))
        .thenAnswer(
            call -> {
              seenUniversity[0] = TenantContext.get();
              countedBy[0] = Thread.currentThread();
              return true;
            });

    listeners.on(uncovered);

    assertThat(queue.awaitIdle(Duration.ofSeconds(10))).isTrue();
    verify(projection).demandUncovered(uncovered);
    assertThat(countedBy[0]).isNotSameAs(Thread.currentThread());
    assertThat(seenUniversity[0]).isEqualTo("UPC");
  }

  @Test
  void aFailureToCountAnUncoveredSearchDoesNotReachWhoPublished() throws InterruptedException {
    when(projection.demandUncovered(uncovered)).thenThrow(new IllegalStateException("boom"));

    assertThatCode(() -> listeners.on(uncovered)).doesNotThrowAnyException();

    assertThat(queue.awaitIdle(Duration.ofSeconds(10))).isTrue();
    verify(projection).demandUncovered(uncovered);
  }

  @Test
  void aQueueThatIsFullDropsTheSearchInsteadOfHoldingItUp() throws InterruptedException {
    UncoveredDemandQueue full = new UncoveredDemandQueue(1, 1);
    CountDownLatch started = new CountDownLatch(1);
    CountDownLatch release = new CountDownLatch(1);
    try {
      // The only worker is held and the only place taken.
      full.offer(
          () -> {
            started.countDown();
            await(release);
          });
      assertThat(started.await(10, TimeUnit.SECONDS)).isTrue();
      assertThat(full.offer(() -> {})).isTrue();
      UsageEventListeners overloaded = new UsageEventListeners(projection, full);

      assertThatCode(() -> overloaded.on(uncovered)).doesNotThrowAnyException();

      release.countDown();
      assertThat(full.awaitIdle(Duration.ofSeconds(10))).isTrue();
    } finally {
      full.stop();
    }
    verify(projection, never()).demandUncovered(uncovered);
  }

  private static void await(CountDownLatch latch) {
    try {
      latch.await(10, TimeUnit.SECONDS);
    } catch (InterruptedException interrupted) {
      Thread.currentThread().interrupt();
    }
  }
}
