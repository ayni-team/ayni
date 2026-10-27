package pe.ayni.booking.interfaces.rest;

import pe.ayni.booking.application.HoursAdjustment;
import pe.ayni.booking.application.HoursWithdrawal;

/**
 * The one thing a tutor should know after changing their availability, when there is one.
 *
 * <p>Booked hours that stand come first: the tutor removed that time and may believe they are free,
 * while a student is still expecting them.
 */
final class HoursNotice {

  private HoursNotice() {}

  /** For a change that can add hours as well as take them away: a date exception. */
  static String of(HoursAdjustment hours) {
    String kept = of(hours.withdrawal());
    if (kept != null) {
      return kept;
    }
    return hours.generation().tutorCanTeach() ? null : AvailabilityPatternResponse.NO_ENABLED_SKILL;
  }

  /** For a change that only takes hours away: a pause. */
  static String of(HoursWithdrawal withdrawal) {
    int kept = withdrawal.bookedKept();
    if (kept == 0) {
      return null;
    }
    return kept == 1
        ? "1 booked hour in this period stands: changing availability does not cancel bookings."
        : kept + " booked hours in this period stand: changing availability does not cancel"
            + " bookings.";
  }
}
