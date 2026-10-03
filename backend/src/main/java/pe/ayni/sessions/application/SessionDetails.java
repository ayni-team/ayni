package pe.ayni.sessions.application;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import pe.ayni.sessions.SessionStatus;
import pe.ayni.sessions.domain.model.ParticipantRole;

/**
 * A session as one of its participants sees it before and during it.
 *
 * @param role which of the two the reader is
 * @param needDescription what the student wrote when booking, read from booking: the tutor reads it
 *     before the session (US07)
 * @param joinOpensAt the first moment the room can be joined
 * @param presenceCheckAt when the presence codes are sent
 * @param presence the reader's own presence code, {@code null} until it is sent
 * @param endConfirmedAt when the reader confirmed the end, {@code null} if they have not
 * @param endedAt when the session closed, {@code null} while it has not
 * @param closesAt when it closes on its own if the participants do not close it
 * @param studentJoinedAt when the student first joined the room, {@code null} before check-in
 * @param tutorJoinedAt when the tutor first joined the room, {@code null} before check-in
 * @param absentParticipantIds participants not checked in at the ten-minute deadline
 */
public record SessionDetails(
    UUID id,
    UUID bookingId,
    UUID studentId,
    UUID tutorId,
    UUID catalogItemId,
    ParticipantRole role,
    SessionStatus status,
    Instant scheduledStart,
    Instant scheduledEnd,
    Instant joinOpensAt,
    Instant startedAt,
    String needDescription,
    Instant presenceCheckAt,
    PresenceState presence,
    Instant studentJoinedAt,
    Instant tutorJoinedAt,
    List<UUID> absentParticipantIds,
    Instant endConfirmedAt,
    Instant endedAt,
    Instant closesAt) {}
