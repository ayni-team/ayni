package pe.ayni.shared.events;

import java.time.Instant;
import java.util.UUID;

/**
 * Published when a session is abandoned because its tutor did not check in within ten minutes.
 *
 * <p>Wallet refunds the booking, notifications tells the student and booking records the tutor's
 * incident, all of them only when the student did check in. Booking marks the reservation as a
 * no-show in either case.
 */
public record SessionAbandoned(
    String tenantId,
    UUID sessionId,
    UUID bookingId,
    UUID tutorId,
    UUID studentId,
    UUID catalogItemId,
    boolean studentCheckedIn,
    Instant occurredOn)
    implements DomainEvent {}
