package pe.ayni.sessions.interfaces.rest;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;

/** A practice room just opened: the id of its session screen and where the web shows it. */
@Schema(name = "PracticeRoom")
record PracticeRoomResponse(
    @Schema(description = "The id the session endpoints take for this room. Random and "
        + "unguessable: whoever holds it, signed in at the same university, can enter while a "
        + "seat is free", example = "9d4c1f0e-2b3a-4c5d-8e6f-7a8b9c0d1e2f")
        UUID sessionId,
    @Schema(description = "The path of the session screen in the web application",
        example = "/sesion/9d4c1f0e-2b3a-4c5d-8e6f-7a8b9c0d1e2f")
        String path) {}
