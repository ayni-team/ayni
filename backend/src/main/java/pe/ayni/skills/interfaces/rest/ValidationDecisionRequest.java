package pe.ayni.skills.interfaces.rest;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * What a coordinator decides about a submission.
 *
 * @param reason why. Required to reject, because the student reads it; optional to approve
 */
@Schema(name = "ValidationDecisionRequest", description = "Approves or rejects a submission")
public record ValidationDecisionRequest(
    @NotNull @Schema(example = "REJECT") Decision decision,
    @Size(max = 500) @Schema(nullable = true, example = "The certificate is not legible")
        String reason) {

  /** The two things a coordinator can decide. */
  public enum Decision {
    APPROVE,
    REJECT
  }
}
