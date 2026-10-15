package pe.ayni.config;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicReference;

/** A clock the tests of isolation can move, so that credits expire without waiting. */
final class TenancyMutableClock extends Clock {

  private final AtomicReference<Instant> now;

  TenancyMutableClock(Instant start) {
    this.now = new AtomicReference<>(start);
  }

  void advance(Duration duration) {
    now.updateAndGet(current -> current.plus(duration));
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
    return now.get();
  }
}
