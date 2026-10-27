package pe.ayni.sessions;

import java.time.Instant;
import java.util.UUID;

/** A session as the other modules see it. */
public record SessionView(
    UUID id,
    UUID bookingId,
    UUID studentId,
    UUID tutorId,
    Instant scheduledStart,
    Instant scheduledEnd,
    SessionStatus status) {}
