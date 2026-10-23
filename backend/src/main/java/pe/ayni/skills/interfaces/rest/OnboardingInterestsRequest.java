package pe.ayni.skills.interfaces.rest;

import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;

/**
 * The last step of the onboarding (US40). Both lists are asked of every student, whatever their
 * term: either may be empty, neither may be left out.
 *
 * @param learning catalogue items the student needs help with
 * @param teaching catalogue items the student can teach; those their record does not back are
 *     skipped
 */
@Schema(
    name = "OnboardingInterestsRequest",
    description = "What the student needs help with and what they can teach")
public record OnboardingInterestsRequest(
    @NotNull
        @Size(max = 50)
        @ArraySchema(
            arraySchema = @Schema(requiredMode = Schema.RequiredMode.REQUIRED),
            schema = @Schema(format = "uuid", example = "b0000000-0000-4000-8000-000000000102"))
        List<@NotNull UUID> learning,
    @NotNull
        @Size(max = 50)
        @ArraySchema(
            arraySchema = @Schema(requiredMode = Schema.RequiredMode.REQUIRED),
            schema = @Schema(format = "uuid", example = "b0000000-0000-4000-8000-000000000101"))
        List<@NotNull UUID> teaching) {}
