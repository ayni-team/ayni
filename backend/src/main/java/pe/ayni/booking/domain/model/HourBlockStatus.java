package pe.ayni.booking.domain.model;

/** Where a one hour block is in its life. */
public enum HourBlockStatus {

  /** Free, and returned by the search. */
  AVAILABLE,

  /** Taken out of circulation while a student fills in the confirmation. */
  HELD,

  /** Confirmed against a booking. */
  BOOKED,

  /** Taken out of circulation for good, after the booking over it was cancelled. */
  RELEASED
}
