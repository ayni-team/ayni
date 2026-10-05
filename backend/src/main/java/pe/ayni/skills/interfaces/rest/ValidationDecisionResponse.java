package pe.ayni.skills.interfaces.rest;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;
import pe.ayni.skills.application.ResolveValidationUseCase;

/** What was decided, and where the skill of the tutor stands now. */
@Schema(name = "ValidationDecision", description = "The outcome of a decision")
public record ValidationDecisionResponse(
    @Schema(example = "e0000000-0000-4000-8000-000000000001") UUID requestId,
    @Schema(example = "APPROVED") String requestStatus,
    @Schema(example = "c0000000-0000-4000-8000-000000000001") UUID skillId,
    @Schema(example = "ENABLED") String skillStatus,
    @Schema(example = "2026-10-04T16:00:00Z") Instant decidedAt) {

  static ValidationDecisionResponse of(ResolveValidationUseCase.Resolution resolution) {
    return new ValidationDecisionResponse(
        resolution.request().getId(),
        resolution.request().getStatus().name(),
        resolution.skill().getId(),
        resolution.skill().getStatus().name(),
        resolution.request().getReviewedAt());
  }
}
