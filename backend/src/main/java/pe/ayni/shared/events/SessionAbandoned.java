package pe.ayni.shared.events;

import java.time.Instant;
import java.util.UUID;

/**
 * Published when a session is abandoned because its tutor did not check in within ten minutes.
 *
 * <p>Wallet refunds and reputation records a tutor incident only when the student did check in.
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
