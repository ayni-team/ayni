package pe.ayni.shared.events;

import java.time.Instant;
import java.util.UUID;
import pe.ayni.shared.domain.Credits;

/**
 * Published by sessions when a session ends and both participants passed the presence check.
 *
 * <p>Several modules react: wallet credits the tutor with earned credits, reputation opens the
 * rating window, recognition advances the progress and notifications tells both participants. None
 * of them is named here, which is the point: a new consequence is a new listener, not a change to
 * the code that closes a session.
 */
public record SessionCompleted(
    String tenantId,
    UUID sessionId,
    UUID bookingId,
    UUID tutorId,
    UUID studentId,
    UUID catalogItemId,
    Credits creditsEarned,
    Instant occurredOn)
    implements DomainEvent {}
