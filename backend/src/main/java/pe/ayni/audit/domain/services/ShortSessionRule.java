package pe.ayni.audit.domain.services;

import java.time.Duration;
import pe.ayni.audit.domain.model.Severity;

/**
 * When a completed session lasted too little compared with what was booked.
 *
 * <p>A session is short when it lasted strictly less than a fraction of the booked time. It is
 * serious when it did not even reach half of that fraction.
 */
public final class ShortSessionRule {

  private final double ratio;

  /**
   * @param ratio the fraction of the booked time under which a session is short, above 0 and at
   *     most 1
   */
  public ShortSessionRule(double ratio) {
    if (!(ratio > 0 && ratio <= 1)) {
      throw new IllegalArgumentException("short-session-ratio must be above 0 and at most 1");
    }
    this.ratio = ratio;
  }

  /** The fraction under which a session is short. */
  public double ratio() {
    return ratio;
  }

  /** Whether the session lasted less than the fraction of the booked time. */
  public boolean isShort(Duration booked, Duration actual) {
    if (booked == null || actual == null || booked.isZero() || booked.isNegative()) {
      return false;
    }
    return actual.compareTo(booked.multipliedBy(percent()).dividedBy(100)) < 0;
  }

  /** How much the finding weighs; only meaningful when {@link #isShort} is true. */
  public Severity severityOf(Duration booked, Duration actual) {
    return actual.compareTo(booked.multipliedBy(percent()).dividedBy(200)) < 0
        ? Severity.HIGH
        : Severity.MEDIUM;
  }

  private long percent() {
    return Math.round(ratio * 100);
  }
}
