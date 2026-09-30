package pe.ayni.booking.domain.model;

import java.io.Serial;

/** The current booking state does not allow cancellation. */
public class BookingCancellationConflict extends RuntimeException {

  @Serial
  private static final long serialVersionUID = 1L;

  public BookingCancellationConflict(String message) {
    super(message);
  }
}
