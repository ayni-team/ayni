package pe.ayni.booking.domain.model;

/** Where a one hour block is in its life. */
public enum HourBlockStatus {

  /** Free, and returned by the search. */
  AVAILABLE,

  /** Taken out of circulation while a student fills in the confirmation. */
  HELD,

  /** Confirmed against a booking. */
  BOOKED,

  /** Released after cancellation; it does not return to circulation automatically. */
  RELEASED,

  /** Withdrawn by an availability change; it can return when the rules allow it again. */
  WITHDRAWN
}
