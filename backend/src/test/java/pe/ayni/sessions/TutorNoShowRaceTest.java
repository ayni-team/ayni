package pe.ayni.sessions;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import pe.ayni.shared.tenancy.TenantContext;

/**
 * Two runs of the job over the same session at the same instant, each on a connection of its own.
 *
 * <p>The session is locked while it is abandoned. Without that lock both read it open with a tutor
 * who never came, both abandon it and both publish {@code SessionAbandoned}; with it the second
 * waits, finds it abandoned and does nothing.
 */
class TutorNoShowRaceTest extends TutorNoShowScenario {

  @Test
  @DisplayName("Two runs at once abandon the session once and publish SessionAbandoned once")
  void twoRunsAtOnceAbandonOnce() throws Exception {

    UUID bookingId = anaBooksTheHour();
    UUID session = sessionOf(bookingId);
    anaCheckedIn(session);
    clock.set(start.plus(Duration.ofMinutes(10)));

    CyclicBarrier bothReady = new CyclicBarrier(2);
    ExecutorService pool = Executors.newFixedThreadPool(2);
    List<Boolean> abandonedNow = new ArrayList<>();
    try {
      List<Future<Boolean>> runs = new ArrayList<>();
      for (int run = 0; run < 2; run++) {
        runs.add(
            pool.submit(
                () -> {
                  bothReady.await();
                  boolean[] abandoned = new boolean[1];
                  TenantContext.runAs(
                      UPC, () -> abandoned[0] = abandonNoShows.abandonFor(session));
                  return abandoned[0];
                }));
      }
      for (Future<Boolean> future : runs) {
        abandonedNow.add(future.get(30, TimeUnit.SECONDS));
      }
    } finally {
      pool.shutdownNow();
    }

    assertThat(abandonedNow).containsExactlyInAnyOrder(true, false);
    assertThat(sessionStatus(session)).isEqualTo("ABANDONED");
    assertThat(announcements.abandonedOf(session)).hasSize(1);
    assertThat(bookingStatus(bookingId)).isEqualTo("NO_SHOW");
    assertThat(refundsOf(bookingId)).isEqualTo(1);
    assertThat(balanceOf(ana)).isEqualTo(5);
    assertThat(incidentsOf(bookingId, "NO_SHOW")).isEqualTo(1);
    assertThat(noShowNoticesOf(session)).isEqualTo(1);
  }
}
