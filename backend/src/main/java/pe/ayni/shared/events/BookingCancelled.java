package pe.ayni.shared.events;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Published by booking when a reservation is cancelled.
 *
 * <p>Cancelling always refunds, so wallet reacts to every one of these. {@code late} says whether it
 * happened within twelve hours of the start, which booking records against whoever cancelled.
 * Matching offers the released blocks again.
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
