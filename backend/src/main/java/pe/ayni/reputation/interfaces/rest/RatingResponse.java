package pe.ayni.reputation.interfaces.rest;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.UUID;

public record RatingResponse(
        @Schema(
                description = "Created rating id",
                example = "11111111-1111-4111-8111-111111111111")
        UUID id) {}