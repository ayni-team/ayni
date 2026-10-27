package pe.ayni.sessions.interfaces.rest;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;
import pe.ayni.sessions.application.PresenceConfirmation;

/** A participant's presence, confirmed. */
@Schema(name = "PresenceConfirmation")
record PresenceResponse(
    @Schema(example = "5b1f0c2a-4d6e-4c7d-8e9f-0a1b2c3d4e5f") UUID sessionId,
    @Schema(description = "When the code was typed in, UTC. The first time, if it was typed again",
        example = "2026-09-30T20:06:30Z")
        Instant confirmedAt) {

  static PresenceResponse of(PresenceConfirmation confirmation) {
    return new PresenceResponse(confirmation.sessionId(), confirmation.confirmedAt());
  }
}
