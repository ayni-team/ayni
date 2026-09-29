package pe.ayni.sessions.application;

import java.time.Instant;
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
    String needDescription) {}
