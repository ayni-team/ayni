package pe.ayni.skills.interfaces.rest;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import pe.ayni.skills.application.SubmitEvidenceUseCase;

/** What the tutor gets back once the evidence was received: it now waits for a coordinator. */
@Schema(name = "EvidenceSubmission", description = "A submission of evidence, waiting for a review")
public record EvidenceSubmissionResponse(
    @Schema(example = "c0000000-0000-4000-8000-000000000001") UUID skillId,
    @Schema(example = "e0000000-0000-4000-8000-000000000001") UUID validationRequestId,
    @Schema(example = "PENDING") String skillStatus,
    @Schema(example = "SUBMITTED") String requestStatus,
    @Schema(example = "2026-10-04T15:30:00Z") Instant submittedAt,
    List<EvidenceFileResponse> files) {

  static EvidenceSubmissionResponse of(SubmitEvidenceUseCase.Submission submission) {
    return new EvidenceSubmissionResponse(
        submission.skill().getId(),
        submission.request().getId(),
        submission.skill().getStatus().name(),
        submission.request().getStatus().name(),
        submission.request().getCreatedAt(),
        submission.files().stream().map(EvidenceFileResponse::of).toList());
  }
}
