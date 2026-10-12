package pe.ayni.skills.interfaces.rest;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import pe.ayni.skills.application.PendingValidationsQuery;

/**
 * A submission in the coordinator's queue: who sent it, for which tool, what they said and which
 * files came with it.
 *
 * @param id the identifier of the request, the one a decision is made on
 * @param note what the student wrote for the reviewer; {@code null} if nothing
 */
@Schema(name = "PendingValidation", description = "Evidence waiting for a coordinator's decision")
public record PendingValidationResponse(
    @Schema(example = "e0000000-0000-4000-8000-000000000001") UUID id,
    @Schema(example = "2026-10-04T15:30:00Z") Instant submittedAt,
    @Schema(nullable = true, example = "Two years using it at work") String note,
    Tool tool,
    Tutor tutor,
    List<EvidenceFileResponse> files) {

  /** The global tool the evidence is for. */
  @Schema(name = "PendingValidationTool")
  public record Tool(
      @Schema(example = "b0000000-0000-4000-8000-000000000001") UUID catalogItemId,
      @Schema(example = "Figma") String name) {}

  /** The student who submitted it, as the coordinator of their university may see them. */
  @Schema(name = "PendingValidationTutor")
  public record Tutor(
      @Schema(example = "11111111-1111-4111-8111-111111111111") UUID id,
      @Schema(example = "Ana Torres") String fullName,
      @Schema(example = "U202310949") String studentCode,
      @Schema(example = "Software Engineering") String career) {}

  static PendingValidationResponse of(PendingValidationsQuery.PendingValidation pending) {
    return new PendingValidationResponse(
        pending.request().getId(),
        pending.request().getCreatedAt(),
        pending.request().getStudentNote(),
        new Tool(pending.item().getId(), pending.item().getName()),
        new Tutor(
            pending.tutor().id(),
            pending.tutor().fullName(),
            pending.tutor().studentCode(),
            pending.tutor().career()),
        pending.files().stream().map(EvidenceFileResponse::of).toList());
  }
}
