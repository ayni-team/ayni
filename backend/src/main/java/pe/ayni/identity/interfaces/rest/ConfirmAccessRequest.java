package pe.ayni.identity.interfaces.rest;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

/**
 * The token of the link the email carried, and nothing else: the university is read from the link,
 * never declared by the client.
 */
public record ConfirmAccessRequest(
        @Schema(
                description = "The token parameter of the link received by email",
                example = "q3JpQ1d8wVx0mYk2Gm9aT5pX0uYv4bNc7eR1sL6hK8o")
        @NotBlank
        String token) {}
