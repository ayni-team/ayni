package pe.ayni.sessions.application;

import java.time.Instant;
import java.util.UUID;
import pe.ayni.sessions.SessionStatus;
import pe.ayni.sessions.domain.model.ParticipantRole;

/**
 * What a participant needs once they are let in: the room, and the session as it now stands.
 *
 * @param roomName the room of the video call. Unguessable, and handed only to a participant who
 *     joins
 * @param startedAt when both participants had checked in and the session began, {@code null}
 *     while it has not
 * @param studentJoinedAt when the student first arrived, {@code null} if they have not
 * @param tutorJoinedAt when the tutor first arrived, {@code null} if they have not
 */
public record JoinedSession(
    UUID sessionId,
    ParticipantRole role,
    String roomName,
    SessionStatus status,
    Instant startedAt,
    Instant scheduledStart,
    Instant scheduledEnd,
    Instant studentJoinedAt,
    Instant tutorJoinedAt) {}
