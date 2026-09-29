package pe.ayni.booking.application;

/**
 * What withdrawing a tutor's hours did.
 *
 * @param withdrawn free or held hours taken out of circulation, and out of the search
 * @param bookedKept hours in the same stretch that are booked and stand: removing availability does
 *     not cancel a booking
 */
public record HoursWithdrawal(int withdrawn, int bookedKept) {

  static HoursWithdrawal nothing() {
    return new HoursWithdrawal(0, 0);
  }
}
