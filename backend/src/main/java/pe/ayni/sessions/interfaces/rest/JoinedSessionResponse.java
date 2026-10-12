package pe.ayni.sessions.interfaces.rest;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;
import pe.ayni.sessions.SessionStatus;
import pe.ayni.sessions.application.JoinedSession;
import pe.ayni.sessions.domain.model.ParticipantRole;

/** A participant let into the room. */
@Schema(name = "JoinedSession")
public record JoinedSessionResponse(
    @Schema(example = "5b1f0c2a-4d6e-4c7d-8e9f-0a1b2c3d4e5f") UUID sessionId,
    @Schema(example = "STUDENT") ParticipantRole role,
    @Schema(description = "Room of the video call. Unguessable; keep it private",
        example = "ayni-3f9c2b7e1d4a4c0e9b8f6a2d5c7e1b3a")
        String roomName,
    @Schema(description = "IN_PROGRESS once anybody has joined", example = "IN_PROGRESS")
        SessionStatus status,
    @Schema(description = "When the first participant joined, UTC",
        example = "2026-09-30T19:52:10Z")
        Instant startedAt,
    @Schema(example = "2026-09-30T20:00:00Z") Instant scheduledStart,
    @Schema(example = "2026-09-30T21:00:00Z") Instant scheduledEnd) {

  static JoinedSessionResponse of(JoinedSession joined) {
    return new JoinedSessionResponse(
        joined.sessionId(),
        joined.role(),
        joined.roomName(),
        joined.status(),
        joined.startedAt(),
        joined.scheduledStart(),
        joined.scheduledEnd());
  }
}
