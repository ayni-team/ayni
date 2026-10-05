package pe.ayni.reputation.interfaces.rest;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

import java.util.Set;

public record RatingRequest(

        @Min(1)
        @Max(5)
        @Schema(
                description = "Stars given by the student to the tutor",
                example = "5")
        Integer stars,

        @Schema(
                description = "Tags selected by the student",
                example = "[\"CLEAR\", \"HELPFUL\"]")
        Set<@Size(max = 40) String> tags,

        @Schema(
                description = "Whether the student was punctual. Used when the tutor rates the student",
                example = "true")
        Boolean wasPunctual,

        @Schema(
                description = "Whether the connection was adequate",
                example = "true")
        Boolean connectionOk,

        @Schema(
                description = "Whether the session could flow normally",
                example = "true")
        Boolean sessionFlowed,

        @Size(max = 500)
        @Schema(
                description = "Optional comment",
                example = "Great tutoring session")
        String comment) {}