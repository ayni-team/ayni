package pe.ayni.booking.domain.model;

/** Who cancelled a booking. {@code SYSTEM} covers a session that ended unverified. */
public enum CancelledBy {
  STUDENT,
  TUTOR,
  SYSTEM
}
