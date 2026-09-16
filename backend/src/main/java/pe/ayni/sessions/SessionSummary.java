package pe.ayni.sessions;

import java.time.Instant;
import java.util.UUID;

/** A completed session, reduced to what a recognition request needs. */
public record SessionSummary(
    UUID sessionId, UUID bookingId, UUID studentId, Instant startedAt, Instant endedAt, int hours) {}
