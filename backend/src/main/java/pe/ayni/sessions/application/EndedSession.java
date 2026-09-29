package pe.ayni.sessions.application;

import java.time.Instant;
import java.util.UUID;
import pe.ayni.sessions.SessionStatus;

/**
 * A participant's confirmation of the end, and where it left the session.
 *
 * @param status still {@code IN_PROGRESS} while the other participant has not confirmed; {@code
 *     COMPLETED} or {@code UNVERIFIED} once closed
 * @param endConfirmedAt when this participant first confirmed the end
 * @param endedAt when the session closed; {@code null} while it waits for the other participant
 * @param closesAt when it closes on its own if the other never confirms
 */
public record EndedSession(
    UUID sessionId,
    SessionStatus status,
    Instant endConfirmedAt,
    Instant endedAt,
    Instant closesAt) {}
