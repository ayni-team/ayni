package pe.ayni.sessions.interfaces.rest;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;
import pe.ayni.sessions.SessionStatus;
import pe.ayni.sessions.application.EndedSession;

/** A participant's confirmation of the end, and where it left the session. */
@Schema(name = "EndedSession")
record EndedSessionResponse(
    @Schema(example = "5b1f0c2a-4d6e-4c7d-8e9f-0a1b2c3d4e5f") UUID sessionId,
    @Schema(description = "IN_PROGRESS while the other participant has not confirmed; COMPLETED "
        + "or UNVERIFIED once closed", example = "COMPLETED")
        SessionStatus status,
    @Schema(description = "When this participant first confirmed the end, UTC",
        example = "2026-09-30T20:58:00Z")
        Instant endConfirmedAt,
    @Schema(description = "When the session closed; null while it waits for the other participant",
        nullable = true, example = "2026-09-30T20:59:10Z")
        Instant endedAt,
    @Schema(description = "When it closes on its own if the other participant never confirms, UTC",
        example = "2026-09-30T21:15:00Z")
        Instant closesAt) {

  static EndedSessionResponse of(EndedSession ended) {
    return new EndedSessionResponse(
        ended.sessionId(), ended.status(), ended.endConfirmedAt(), ended.endedAt(), ended.closesAt());
  }
}
