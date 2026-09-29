package pe.ayni.identity.interfaces.rest;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;
import pe.ayni.identity.application.ConfirmAccessResult;

/** The session a confirmed link opened. */
@Schema(name = "OpenedSession")
public record ConfirmAccessResponse(
        @Schema(
                description =
                        "Session token in clear. Only its hash is stored: keep it, it is not"
                                + " shown again",
                example = "Zp8wQ4nB1cX7vL2kT9sR6mY3hF0dJ5gA8eU1iO4pW2q")
        String sessionToken,
        @Schema(description = "University of the account, read from the link", example = "UPC")
        String tenantId,
        @Schema(example = "11111111-1111-4111-8111-111111111111") UUID userId,
        @Schema(description = "When the session ends, UTC", example = "2026-10-06T05:30:00Z")
        Instant expiresAt) {

    static ConfirmAccessResponse from(ConfirmAccessResult result) {
        return new ConfirmAccessResponse(
                result.sessionToken(),
                result.tenantId(),
                result.userId(),
                result.expiresAt());
    }
}
