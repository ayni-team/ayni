package pe.ayni.sessions.interfaces.rest;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;
import pe.ayni.sessions.SessionStatus;
import pe.ayni.sessions.application.SessionDetails;
import pe.ayni.sessions.domain.model.ParticipantRole;

/** A session, as one of its participants sees it. The room name is only given by joining. */
@Schema(name = "Session")
public record SessionResponse(
    @Schema(example = "5b1f0c2a-4d6e-4c7d-8e9f-0a1b2c3d4e5f") UUID id,
    @Schema(example = "d1f0c2a4-5b6e-4c7d-8e9f-0a1b2c3d4e5f") UUID bookingId,
    @Schema(example = "11111111-1111-4111-8111-111111111111") UUID studentId,
    @Schema(example = "22222222-2222-4222-8222-222222222222") UUID tutorId,
    @Schema(description = "The course the session is about",
        example = "b0000000-0000-4000-8000-000000000102")
        UUID catalogItemId,
    @Schema(description = "Which of the two the reader is", example = "TUTOR") ParticipantRole role,
    @Schema(example = "SCHEDULED") SessionStatus status,
    @Schema(description = "UTC", example = "2026-09-30T20:00:00Z") Instant scheduledStart,
    @Schema(description = "UTC", example = "2026-09-30T21:00:00Z") Instant scheduledEnd,
    @Schema(description = "First moment the room can be joined, UTC",
        example = "2026-09-30T19:45:00Z")
        Instant joinOpensAt,
    @Schema(description = "When the first participant joined; null until then", nullable = true,
        example = "2026-09-30T19:52:10Z")
        Instant startedAt,
    @Schema(description = "What the student needs help with, written when booking",
        example = "Normal forms before Friday's exam")
        String needDescription) {

  static SessionResponse of(SessionDetails session) {
    return new SessionResponse(
        session.id(),
        session.bookingId(),
        session.studentId(),
        session.tutorId(),
        session.catalogItemId(),
        session.role(),
        session.status(),
        session.scheduledStart(),
        session.scheduledEnd(),
        session.joinOpensAt(),
        session.startedAt(),
        session.needDescription());
  }
}
