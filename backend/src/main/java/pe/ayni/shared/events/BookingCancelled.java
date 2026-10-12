package pe.ayni.shared.events;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Published by booking when a reservation is cancelled.
 *
 * <p>Wallet refunds the student for every one of these except a late cancellation by the student
 * themselves. {@code late} says whether it happened less than twelve hours before the start, and
 * {@code cancelledBy} says who did it: a tutor's cancellation refunds whatever the hour, and booking
 * records a late one against the tutor. Matching offers the released blocks again.
 */
public record BookingCancelled(
    String tenantId,
    UUID bookingId,
    UUID studentId,
    UUID tutorId,
    List<UUID> releasedBlockIds,
    CancelledBy cancelledBy,
    boolean late,
    Instant occurredOn)
    implements DomainEvent {

  /** Who cancelled. {@code SYSTEM} covers a session that ended unverified. */
  public enum CancelledBy {
    STUDENT,
    TUTOR,
    SYSTEM
  }
}
