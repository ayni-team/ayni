package pe.ayni.skills.interfaces.rest;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

/**
 * A tool the student did not find in the catalogue.
 *
 * @param confirmDistinct {@code true} once the student saw the similar skills the first answer
 *     listed and theirs is a different one
 */
@Schema(name = "ProposeSkillRequest", description = "A tool to add to the catalogue")
public record ProposeSkillRequest(
    @NotBlank @Size(max = 160) @Schema(example = "Figma") String name,
    @NotNull @Schema(example = "b0000000-0000-4000-8000-000000000001") UUID categoryId,
    @Size(max = 500) @Schema(nullable = true, example = "Interface design and prototyping tool")
        String description,
    @Schema(
            description = "Send true after seeing the similar skills, when yours is a different one",
            defaultValue = "false")
        boolean confirmDistinct) {}
