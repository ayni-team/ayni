package pe.ayni.recognition.interfaces.rest;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;
import pe.ayni.recognition.domain.model.RecognitionRequest;

/** What was decided about a request, by whom and when. */
@Schema(name = "RecognitionDecision", description = "The decision about a recognition request")
public record DecisionResponse(
    @Schema(example = "d0000000-0000-4000-8000-000000000001") UUID requestId,
    @Schema(allowableValues = {"APPROVED", "REJECTED"}, example = "APPROVED") String status,
    @Schema(example = "22222222-2222-4222-8222-222222222222") UUID reviewedBy,
    @Schema(example = "2026-10-08T15:30:00Z") Instant reviewedAt,
    @Schema(example = "The hours match the extracurricular credit of the programme") String reason) {

  static DecisionResponse of(RecognitionRequest request) {
    return new DecisionResponse(
        request.getId(),
        request.getStatus().name(),
        request.getReviewedBy(),
        request.getReviewedAt(),
        request.getDecisionReason());
  }
}
