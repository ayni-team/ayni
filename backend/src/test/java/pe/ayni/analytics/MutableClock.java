package pe.ayni.analytics;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicReference;

/**
 * A clock a test can move: a search at half past ten at night in Lima is already the next day in
 * UTC.
 *
 * <p>The same idea as the one of identity and booking, kept here because a module's tests do not
 * reach into another module's test package.
 */
final class MutableClock extends Clock {

  private final AtomicReference<Instant> now;
  private final ZoneId zone;

  MutableClock(Instant start) {
    this(new AtomicReference<>(start), ZoneOffset.UTC);
  }

  private MutableClock(AtomicReference<Instant> now, ZoneId zone) {
    this.now = now;
    this.zone = zone;
  }

  void set(Instant instant) {
    now.set(instant);
  }

  @Override
  public ZoneId getZone() {
    return zone;
  }

  @Override
  public Clock withZone(ZoneId zone) {
    return new MutableClock(now, zone);
  }

  @Override
  public Instant instant() {
    return now.get();
  }
}
