package pe.ayni.skills.interfaces.rest;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

/** The catalogue item the tutor wants to offer. */
public record OfferSkillRequest(
    @NotNull @Schema(example = "b0000000-0000-4000-8000-000000000101") UUID catalogItemId) {}
