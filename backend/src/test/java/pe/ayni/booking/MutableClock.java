package pe.ayni.booking;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicReference;

/**
 * A clock a test can move: five minutes later, the hold has run out.
 *
 * <p>Every view of it taken with {@link #withZone} reads the same instant, so a use case asking for
 * the university's local date and another asking for the instant agree on what now is. Shared
 * between threads, which the concurrency tests need.
 */
public final class MutableClock extends Clock {

  private final AtomicReference<Instant> now;
  private final ZoneId zone;

  MutableClock(Instant start) {
    this(new AtomicReference<>(start), ZoneOffset.UTC);
  }

  private MutableClock(AtomicReference<Instant> now, ZoneId zone) {
    this.now = now;
    this.zone = zone;
  }

  public void set(Instant instant) {
    now.set(instant);
  }

  public void advance(Duration duration) {
    now.updateAndGet(current -> current.plus(duration));
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
