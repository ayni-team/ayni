package pe.ayni.booking.application;

/**
 * How a tutor's hours changed after their availability did: what left, what stands, what appeared.
 *
 * @param withdrawal the hours the new rules no longer produce
 * @param generation the hours the new rules produce and did not exist yet, such as those of an ADD
 */
public record HoursAdjustment(HoursWithdrawal withdrawal, HoursGeneration generation) {

  /** Nothing to adjust: the change falls outside the hours that exist. */
  static HoursAdjustment none() {
    return new HoursAdjustment(HoursWithdrawal.nothing(), HoursGeneration.created(0));
  }
}
