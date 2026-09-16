package pe.ayni.shared.events;

import java.time.Instant;
import java.util.UUID;
import pe.ayni.shared.domain.Credits;

/**
 * Published by the sessions module once both participants confirmed a session ended.
 *
 * <p>Several modules react: wallet credits the tutor with earned credits, reputation opens the
 * rating window, recognition advances the progress towards the certificate and notifications tells
 * both participants. None of them is named here, which is the point: a new consequence is a new
 * listener, not a change to the code that closes a session.
 */
public record SessionCompleted(
    String tenantId,
    UUID sessionId,
    UUID tutorId,
    UUID studentId,
    Credits creditsEarned,
    Instant occurredOn)
    implements DomainEvent {}
