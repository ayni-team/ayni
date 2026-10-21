package pe.ayni.config.demo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** The clock of the demo profile, without Spring. */
class AdjustableClockTest {

  private static final Instant REAL = Instant.parse("2026-10-21T15:00:00Z");

  /** Stands for the real time, which the test moves on its own to show the clock keeps running. */
  private static final class RealTime extends Clock {
    private final AtomicReference<Instant> now = new AtomicReference<>(REAL);

    void passes(Duration duration) {
      now.updateAndGet(current -> current.plus(duration));
    }

    @Override
    public ZoneId getZone() {
      return ZoneOffset.UTC;
    }

    /** The same real time from another zone. */
    @Override
    public Clock withZone(ZoneId other) {
      if (other.equals(ZoneOffset.UTC)) {
        return this;
      }
      return new Clock() {
        @Override
        public ZoneId getZone() {
          return other;
        }

        @Override
        public Clock withZone(ZoneId again) {
          return RealTime.this.withZone(again);
        }

        @Override
        public Instant instant() {
          return RealTime.this.instant();
        }
      };
    }

    @Override
    public Instant instant() {
      return now.get();
    }
  }

  private final RealTime real = new RealTime();
  private final AdjustableClock clock = new AdjustableClock(real);

  @Test
  @DisplayName("The clock keeps running after it is moved")
  void theClockKeepsRunningAfterItIsMoved() {
    assertThat(clock.instant()).isEqualTo(REAL);
    assertThat(clock.isMoved()).isFalse();

    clock.advance(Duration.ofHours(2));
    assertThat(clock.instant()).isEqualTo(REAL.plus(Duration.ofHours(2)));

    real.passes(Duration.ofMinutes(3));
    assertThat(clock.instant()).isEqualTo(REAL.plus(Duration.ofHours(2)).plusSeconds(180));
    assertThat(clock.realInstant()).isEqualTo(REAL.plusSeconds(180));
    assertThat(clock.offset()).isEqualTo(Duration.ofHours(2));
    assertThat(clock.isMoved()).isTrue();
  }

  @Test
  @DisplayName("moves add up, forwards and backwards")
  void movesAddUp() {
    clock.advance(Duration.ofDays(31));
    clock.advance(Duration.ofHours(-5));

    assertThat(clock.offset()).isEqualTo(Duration.ofDays(31).minusHours(5));
    assertThat(clock.instant()).isEqualTo(REAL.plus(Duration.ofDays(31)).minusSeconds(5 * 3600));
  }

  @Test
  @DisplayName("set at an instant it reads that instant and runs from there")
  void setAtAnInstant() {
    Instant at = Instant.parse("2026-11-30T19:50:00Z");

    clock.setTo(at);
    assertThat(clock.instant()).isEqualTo(at);

    real.passes(Duration.ofSeconds(10));
    assertThat(clock.instant()).isEqualTo(at.plusSeconds(10));
  }

  @Test
  @DisplayName("reset reads the real time again")
  void resetReadsTheRealTime() {
    clock.setTo(Instant.parse("2025-01-01T00:00:00Z"));

    clock.reset();

    assertThat(clock.instant()).isEqualTo(REAL);
    assertThat(clock.offset()).isZero();
    assertThat(clock.isMoved()).isFalse();
  }

  @Test
  @DisplayName("another zone sees the same moved time, also after the clock moves again")
  void anotherZoneSeesTheSameMovedTime() {
    Clock lima = clock.withZone(ZoneId.of("America/Lima"));

    clock.advance(Duration.ofDays(2));

    assertThat(lima.instant()).isEqualTo(clock.instant());
    assertThat(lima.getZone()).isEqualTo(ZoneId.of("America/Lima"));
    assertThat(LocalDate.now(lima)).isEqualTo(LocalDate.of(2026, 10, 23));
    assertThat(clock.withZone(ZoneOffset.UTC)).isSameAs(clock);
  }

  @Test
  @DisplayName("a move further than the limit is refused and leaves the clock where it was")
  void aMoveFurtherThanTheLimitIsRefused() {
    clock.advance(Duration.ofHours(1));

    assertThatThrownBy(() -> clock.advance(AdjustableClock.LIMIT))
        .isInstanceOf(InvalidClockValue.class);
    assertThatThrownBy(() -> clock.setTo(REAL.minus(AdjustableClock.LIMIT).minusSeconds(1)))
        .isInstanceOf(InvalidClockValue.class);
    assertThatThrownBy(() -> clock.setTo(Instant.MAX)).isInstanceOf(InvalidClockValue.class);

    assertThat(clock.offset()).isEqualTo(Duration.ofHours(1));
  }

  @Test
  @DisplayName("moves from many threads at once are all applied")
  void movesFromManyThreadsAreAllApplied() throws Exception {
    int threads = 8;
    int movesEach = 500;
    CyclicBarrier start = new CyclicBarrier(threads);
    ExecutorService pool = Executors.newFixedThreadPool(threads);
    try {
      List<Future<?>> done = new ArrayList<>();
      for (int i = 0; i < threads; i++) {
        done.add(
            pool.submit(
                () -> {
                  start.await();
                  for (int move = 0; move < movesEach; move++) {
                    clock.advance(Duration.ofSeconds(1));
                  }
                  return null;
                }));
      }
      for (Future<?> each : done) {
        each.get();
      }
    } finally {
      pool.shutdownNow();
    }

    assertThat(clock.offset()).isEqualTo(Duration.ofSeconds((long) threads * movesEach));
  }
}
