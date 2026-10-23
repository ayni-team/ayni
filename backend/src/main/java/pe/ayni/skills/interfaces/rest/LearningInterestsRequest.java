package pe.ayni.skills.interfaces.rest;

import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;

/**
 * The whole set of what a student needs help with (US41). An empty list leaves them with none.
 *
 * @param catalogItemIds catalogue items visible to the student's university
 */
@Schema(name = "LearningInterestsRequest", description = "Everything the student needs help with")
public record LearningInterestsRequest(
    @NotNull
        @Size(max = 50)
        @ArraySchema(
            arraySchema = @Schema(requiredMode = Schema.RequiredMode.REQUIRED),
            schema = @Schema(format = "uuid", example = "b0000000-0000-4000-8000-000000000102"))
        List<@NotNull UUID> catalogItemIds) {}
