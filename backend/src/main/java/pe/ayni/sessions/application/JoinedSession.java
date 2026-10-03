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
