package pe.ayni.booking.domain.model;

import java.util.Locale;

/**
 * The two halves a person's bookings are read in (US04, US21): what is still ahead, the nearest
 * first, and the history, the most recent first. {@link Booking#isUpcomingAt} decides which half a
 * booking is in.
 */
public enum BookingTimeline {
  UPCOMING,
  HISTORY;

  /**
   * The half a request names, in any case; none means the upcoming one.
   *
   * @throws BookingRuleViolation when it names neither
   */
  public static BookingTimeline named(String name) {
    if (name == null || name.isBlank()) {
      return UPCOMING;
    }
    try {
      return valueOf(name.strip().toUpperCase(Locale.ROOT));
    } catch (IllegalArgumentException unknown) {
      throw new BookingRuleViolation("view must be UPCOMING or HISTORY");
    }
  }
}
