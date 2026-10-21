package pe.ayni.sessions.interfaces.rest;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;
import pe.ayni.sessions.SessionStatus;
import pe.ayni.sessions.application.SessionOfBooking;

/** The session of a booking: what a list of tutorings needs to open it. No room name. */
@Schema(name = "SessionOfBooking")
public record SessionOfBookingResponse(
    @Schema(description = "Identifier to read, join and end the session with",
        example = "5b1f0c2a-4d6e-4c7d-8e9f-0a1b2c3d4e5f")
        UUID sessionId,
    @Schema(example = "d1f0c2a4-5b6e-4c7d-8e9f-0a1b2c3d4e5f") UUID bookingId,
    @Schema(example = "SCHEDULED") SessionStatus status,
    @Schema(description = "First moment the room can be joined, UTC",
        example = "2026-09-30T19:45:00Z")
        Instant joinOpensAt,
    @Schema(description = "When the booked hours end, UTC", example = "2026-09-30T21:00:00Z")
        Instant scheduledEnd) {

  static SessionOfBookingResponse of(SessionOfBooking session) {
    return new SessionOfBookingResponse(
        session.sessionId(),
        session.bookingId(),
        session.status(),
        session.joinOpensAt(),
        session.scheduledEnd());
  }
}
