package pe.ayni.shared.demo;

import java.time.Instant;
import java.util.UUID;

/**
 * What the history of the {@code demo} profile asks of booking, in the university bound to the
 * thread and at the time the clock reads.
 */
public interface DemoBookings {

  /** A tutoring booked for the history. */
  record BookedTutoring(UUID bookingId, Instant startsAt, Instant endsAt) {}

  /**
   * Holds and books, as the student, the first {@code hours} consecutive hours the tutor has open
   * from {@code earliest}, in a course the tutor teaches.
   *
   * @throws java.util.NoSuchElementException when the tutor has no such hours or teaches nothing
   */
  BookedTutoring bookFirstOpen(
      UUID studentId, UUID tutorId, Instant earliest, int hours, String needDescription);

  /** Cancels a booking as one of its participants, well before it starts. */
  void cancel(UUID userId, UUID bookingId);

  /** Whether the university has any booking that starts before that instant. */
  boolean anyStartingBefore(Instant instant);
}
