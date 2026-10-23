package pe.ayni.config.demo;

import java.time.Clock;
import java.time.DateTimeException;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

/**
 * The clock of the {@code demo} profile: the real time plus an offset that can be moved while the
 * application runs.
 *
 * <p>Time keeps running: moving the clock two hours ahead does not stop it at that instant, it
 * makes every reading two hours later than the real one. That is what lets a presentation show
 * credits that expire, the window of a session that opens or a hold that runs out without waiting
 * and without restarting.
 *
 * <p>The offset is shared by every view of the clock, those of {@link #withZone} included, so a use
 * case that reads the date of a university in its own zone sees the same moved time. Moving it is
 * safe from several threads at once: each move is applied to the offset as it is at that moment.
 *
 * <p>An offset is never more than {@link #LIMIT} away from the real time. A demonstration moves the
 * clock days or months, and an instant far enough would make every reading of the application fail.
 */
public final class AdjustableClock extends Clock {

  /** How far from the real time the clock may be taken, either way. */
  static final Duration LIMIT = Duration.ofDays(3650);

  private final Clock real;
  private final AtomicReference<Duration> offset;

  /** A clock that reads the real time of {@code real} until it is moved. */
  public AdjustableClock(Clock real) {
    this(real, new AtomicReference<>(Duration.ZERO));
  }

  private AdjustableClock(Clock real, AtomicReference<Duration> offset) {
    this.real = Objects.requireNonNull(real, "real must not be null");
    this.offset = offset;
  }

  /** Moves the clock by that much, forwards or backwards, from where it is now. */
  public Duration advance(Duration by) {
    Objects.requireNonNull(by, "by must not be null");
    return offset.updateAndGet(
        current -> {
          try {
            return withinLimit(current.plus(by));
          } catch (ArithmeticException tooFar) {
            throw new InvalidClockValue("The clock cannot be moved that far");
          }
        });
  }

  /** Takes the clock to that instant; from there on it keeps running. */
  public Duration setTo(Instant at) {
    Objects.requireNonNull(at, "at must not be null");
    Duration wanted;
    try {
      wanted = withinLimit(Duration.between(real.instant(), at));
    } catch (ArithmeticException | DateTimeException tooFar) {
      throw new InvalidClockValue("The clock cannot be moved that far");
    }
    offset.set(wanted);
    return wanted;
  }

  /** Takes the clock back to the real time. */
  public void reset() {
    offset.set(Duration.ZERO);
  }

  /** How far the clock is from the real time: positive ahead, negative behind. */
  public Duration offset() {
    return offset.get();
  }

  /** The real time, which the clock reads plus its offset. */
  public Instant realInstant() {
    return real.instant();
  }

  /** Whether the clock reads anything but the real time. */
  public boolean isMoved() {
    return !offset.get().isZero();
  }

  @Override
  public Instant instant() {
    return real.instant().plus(offset.get());
  }

  @Override
  public ZoneId getZone() {
    return real.getZone();
  }

  /** The same moved time seen from another zone: both share one offset. */
  @Override
  public Clock withZone(ZoneId zone) {
    if (zone.equals(real.getZone())) {
      return this;
    }
    return new AdjustableClock(real.withZone(zone), offset);
  }

  private static Duration withinLimit(Duration wanted) {
    if (wanted.abs().compareTo(LIMIT) > 0) {
      throw new InvalidClockValue(
          "The clock cannot be moved more than " + LIMIT.toDays() + " days from the real time");
    }
    return wanted;
  }
}
