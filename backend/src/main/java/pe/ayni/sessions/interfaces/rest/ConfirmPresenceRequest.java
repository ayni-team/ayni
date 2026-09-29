package pe.ayni.sessions.interfaces.rest;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

/** The code a participant received by email. */
@Schema(name = "ConfirmPresenceRequest")
record ConfirmPresenceRequest(
    @Schema(description = "The six digits of the email", example = "042917")
        @NotNull(message = "is required")
        @Pattern(regexp = "\\d{6}", message = "must be six digits")
        String code) {}
