package pe.ayni.booking;

import java.util.UUID;

/**
 * What booking offers to the other modules.
 *
 * <p>Implemented by a class in {@code booking.application}.
 */
public interface BookingApi {

  /** @throws java.util.NoSuchElementException when the booking does not exist in the current tenant */
  BookingView requireBooking(UUID bookingId);
}
