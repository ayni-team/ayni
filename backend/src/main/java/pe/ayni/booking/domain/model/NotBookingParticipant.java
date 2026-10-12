package pe.ayni.booking.domain.model;

import java.io.Serial;

/** The person acting on a booking is neither its student nor its tutor. */
public class NotBookingParticipant extends RuntimeException {

  @Serial
  private static final long serialVersionUID = 1L;

  public NotBookingParticipant(String message) {
    super(message);
  }
}
