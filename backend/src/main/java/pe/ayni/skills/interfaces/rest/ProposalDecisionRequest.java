package pe.ayni.skills.interfaces.rest;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

/**
 * What a moderator decides about a proposal.
 *
 * @param catalogItemId the existing item to join the proposal to. Required for MERGE, refused for
 *     the other decisions
 * @param reason why. Required to reject, because the student reads it; optional otherwise
 */
@Schema(name = "ProposalDecisionRequest", description = "Approves, joins or rejects a proposal")
public record ProposalDecisionRequest(
    @NotNull @Schema(example = "MERGE") Decision decision,
    @Schema(nullable = true, example = "b0000000-0000-4000-8000-000000000101") UUID catalogItemId,
    @Size(max = 500) @Schema(nullable = true, example = "It is the same tool as Node.js")
        String reason) {

  /** The three things a moderator can decide. */
  public enum Decision {
    APPROVE,
    MERGE,
    REJECT
  }
}
