package pe.ayni.shared.events;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Published by sessions when a session ends without every participant confirming presence.
 *
 * <p>The tutor earns nothing, wallet refunds the student and audit records the failed check.
 */
public record SessionUnverified(
    String tenantId,
    UUID sessionId,
    UUID bookingId,
    UUID tutorId,
    UUID studentId,
    List<UUID> unconfirmedUserIds,
    Instant occurredOn)
    implements DomainEvent {}
