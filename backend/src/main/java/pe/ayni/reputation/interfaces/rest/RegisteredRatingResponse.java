package pe.ayni.reputation.interfaces.rest;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;
import pe.ayni.reputation.application.RegisteredRating;

/**
 * What the caller left about a session. Only the fields of the caller's own direction travel: a
 * student's answer has no facts and a tutor's has no stars or tags. Who rated and who was rated
 * never travel.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(name = "RegisteredRating", description = "What the caller registered about the session")
record RegisteredRatingResponse(
        @Schema(description = "Whether the caller already rated the session", example = "true")
                boolean rated,
        @Schema(description = "Stars the student gave. Student only", example = "5") Integer stars,
        @Schema(description = "Tags the student chose. Student only", example = "[\"CLEAR\", \"HELPFUL\"]")
                List<String> tags,
        @Schema(description = "Whether the student was punctual. Tutor only", example = "true")
                Boolean wasPunctual,
        @Schema(description = "Whether the connection held. Tutor only", example = "true")
                Boolean connectionOk,
        @Schema(description = "Whether the session could flow. Tutor only", example = "true")
                Boolean sessionFlowed,
        @Schema(description = "Optional comment", example = "Great tutoring session") String comment,
        @Schema(description = "When it was registered, in UTC", example = "2026-10-20T21:00:00Z")
                Instant createdAt) {

    static RegisteredRatingResponse from(RegisteredRating rating) {
        return new RegisteredRatingResponse(
                rating.rated(),
                rating.stars(),
                rating.tags(),
                rating.wasPunctual(),
                rating.connectionOk(),
                rating.sessionFlowed(),
                rating.comment(),
                rating.createdAt());
    }
}
