package pe.ayni.sessions.application;

import java.time.Instant;
import java.util.UUID;
import pe.ayni.sessions.SessionStatus;

/**
 * The session of a booking, as much of it as a list needs to send a participant into it.
 *
 * @param sessionId the session's identifier, which the booking does not know
 * @param joinOpensAt the first moment the room can be joined
 * @param scheduledEnd when the booked hours end, the last moment it can be joined
 */
public record SessionOfBooking(
    UUID sessionId,
    UUID bookingId,
    SessionStatus status,
    Instant joinOpensAt,
    Instant scheduledEnd) {}
