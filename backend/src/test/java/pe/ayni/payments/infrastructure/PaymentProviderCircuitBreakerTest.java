package pe.ayni.payments.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class PaymentProviderCircuitBreakerTest {

  @Test
  void opensAfterConsecutiveFailuresAndClosesAfterOneSuccessfulProbe() {
    MutableClock clock = new MutableClock(Instant.parse("2026-10-06T12:00:00Z"));
    PaymentProviderCircuitBreaker breaker =
        new PaymentProviderCircuitBreaker(clock, 2, Duration.ofSeconds(30));

    assertThat(breaker.tryAcquire()).isTrue();
    breaker.recordFailure();
    assertThat(breaker.stateName()).isEqualTo("CLOSED");
    assertThat(breaker.tryAcquire()).isTrue();
    breaker.recordFailure();
    assertThat(breaker.stateName()).isEqualTo("OPEN");
    assertThat(breaker.tryAcquire()).isFalse();

    clock.advance(Duration.ofSeconds(30));
    assertThat(breaker.tryAcquire()).isTrue();
    assertThat(breaker.stateName()).isEqualTo("HALF_OPEN");
    assertThat(breaker.tryAcquire()).isFalse();
    breaker.recordSuccess();

    assertThat(breaker.stateName()).isEqualTo("CLOSED");
    assertThat(breaker.tryAcquire()).isTrue();
  }

  @Test
  void reopensWhenTheHalfOpenProbeFails() {
    MutableClock clock = new MutableClock(Instant.parse("2026-10-06T12:00:00Z"));
    PaymentProviderCircuitBreaker breaker =
        new PaymentProviderCircuitBreaker(clock, 1, Duration.ofSeconds(10));

    breaker.tryAcquire();
    breaker.recordFailure();
    clock.advance(Duration.ofSeconds(10));
    assertThat(breaker.tryAcquire()).isTrue();
    breaker.recordFailure();

    assertThat(breaker.stateName()).isEqualTo("OPEN");
    assertThat(breaker.tryAcquire()).isFalse();
  }

  private static final class MutableClock extends Clock {

    private final AtomicReference<Instant> instant;

    private MutableClock(Instant initial) {
      instant = new AtomicReference<>(initial);
    }

    private void advance(Duration duration) {
      instant.updateAndGet(current -> current.plus(duration));
    }

    @Override
    public ZoneId getZone() {
      return ZoneOffset.UTC;
    }

    @Override
    public Clock withZone(ZoneId zone) {
      return this;
    }

    @Override
    public Instant instant() {
      return instant.get();
    }
  }
}
