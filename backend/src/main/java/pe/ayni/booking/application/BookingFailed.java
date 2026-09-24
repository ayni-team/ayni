package pe.ayni.booking.application;

/**
 * A confirmation that failed for a reason nobody refused on purpose: a database error, a bug.
 *
 * <p>By the time this is thrown the confirmation's transaction has rolled back, so nothing was
 * charged, and the student's holds have been dealt with. The message says both, because a student
 * who sees an error while paying wants to know first whether they paid.
 */
public class BookingFailed extends RuntimeException {

  private static final long serialVersionUID = 1L;

  BookingFailed(Throwable cause, boolean holdsReleased) {
    super(
        holdsReleased
            ? "The booking could not be completed. Nothing was charged and the hours you held "
                + "are free again"
            : "The booking could not be completed. Nothing was charged; the hours you held will "
                + "be free again within five minutes",
        cause);
  }
}
