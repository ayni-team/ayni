package pe.ayni.booking;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * What booking offers to the other modules.
 *
 * <p>Implemented by a class in {@code booking.application}.
 */
public interface BookingApi {

  /** @throws java.util.NoSuchElementException when the booking does not exist in the current tenant */
  BookingView requireBooking(UUID bookingId);

  /** Whether this booking remains confirmed, read under a lock while its session is scheduled. */
  boolean isConfirmed(UUID bookingId);

  /**
   * The tutor's hours in the current university that have not started at {@code from} and can still
   * be booked, earliest first.
   *
   * <p>Added for matching, which keeps its search projection from events and needs to ask which
   * hours a tutor already has when a course is enabled after they were generated, or when a
   * cancellation gives hours back. An hour somebody holds counts as open: a hold lasts five minutes
   * and ends either in a booking, which is announced, or back in circulation, which is not.
   */
  List<OpenHourView> openHoursOf(UUID tutorId, Instant from);
}
