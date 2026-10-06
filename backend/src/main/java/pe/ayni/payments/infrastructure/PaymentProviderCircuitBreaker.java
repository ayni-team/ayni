package pe.ayni.payments.infrastructure;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

final class PaymentProviderCircuitBreaker {

  private enum State {
    CLOSED,
    OPEN,
    HALF_OPEN
  }

  private final Clock clock;
  private final int failureThreshold;
  private final Duration openDuration;

  private State state = State.CLOSED;
  private int consecutiveFailures;
  private Instant openUntil;
  private boolean probeInFlight;

  PaymentProviderCircuitBreaker(Clock clock, int failureThreshold, Duration openDuration) {
    if (failureThreshold < 1) {
      throw new IllegalArgumentException("failureThreshold must be positive");
    }
    if (openDuration.isZero() || openDuration.isNegative()) {
      throw new IllegalArgumentException("openDuration must be positive");
    }
    this.clock = clock;
    this.failureThreshold = failureThreshold;
    this.openDuration = openDuration;
  }

  synchronized boolean tryAcquire() {
    if (state == State.OPEN) {
      if (clock.instant().isBefore(openUntil)) {
        return false;
      }
      state = State.HALF_OPEN;
    }

    if (state == State.HALF_OPEN) {
      if (probeInFlight) {
        return false;
      }
      probeInFlight = true;
    }
    return true;
  }

  synchronized void recordSuccess() {
    if (state == State.HALF_OPEN) {
      close();
    } else if (state == State.CLOSED) {
      consecutiveFailures = 0;
    }
  }

  synchronized void recordFailure() {
    if (state == State.HALF_OPEN) {
      open();
      return;
    }
    if (state == State.CLOSED && ++consecutiveFailures >= failureThreshold) {
      open();
    }
  }

  private void open() {
    state = State.OPEN;
    openUntil = clock.instant().plus(openDuration);
    probeInFlight = false;
  }

  private void close() {
    state = State.CLOSED;
    consecutiveFailures = 0;
    openUntil = null;
    probeInFlight = false;
  }

  synchronized String stateName() {
    return state.name();
  }
}
