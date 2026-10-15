package pe.ayni.shared.events;

import java.time.Instant;
import java.util.UUID;

/**
 * Published by sessions when a session that took place closes, with whether anything was drawn on
 * its whiteboard (US10).
 *
 * <p>It is what an audit rule about sessions with no activity on the board needs ({@code
 * NO_BOARD_ACTIVITY} in {@code audit.anomalies}), without asking sessions for the content. Nobody
 * listens to it yet.
 *
 * @param hadActivity whether at least one operation was applied to the board
 */
public record WhiteboardClosed(
    String tenantId,
    UUID sessionId,
    UUID tutorId,
    UUID studentId,
    boolean hadActivity,
    Instant occurredOn)
    implements DomainEvent {}
