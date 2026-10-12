package pe.ayni.shared.events;

import java.time.Instant;
import java.util.UUID;

/**
 * Published when a session is abandoned because at least one participant did not check in within
 * ten minutes of its scheduled start: the tutor, the student, or both.
 *
 * <p>{@code studentCheckedIn} tells the three cases apart, since a session is only abandoned when
 * somebody is missing. When it is true the student came, so the tutor was the one missing: wallet
 * refunds the booking, notifications tells the student and booking records the tutor's incident.
 * When it is false the student was absent, whether or not the tutor came: nobody is refunded, told
 * or blamed. Booking marks the reservation as a no-show in every case.
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
