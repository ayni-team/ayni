package pe.ayni.skills.interfaces.rest;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;
import pe.ayni.skills.application.ResolveProposalUseCase;

/**
 * What was decided and where the proposal ended up.
 *
 * @param catalogItem the item it ended up in; {@code null} for a rejection
 */
@Schema(name = "ProposalDecision", description = "The outcome of a decision on a proposal")
public record ProposalDecisionResponse(
    @Schema(example = "c0000000-0000-4000-8000-000000000001") UUID proposalId,
    @Schema(allowableValues = {"APPROVED", "MERGED", "REJECTED"}, example = "APPROVED") String status,
    @Schema(example = "2026-10-05T09:00:00Z") Instant decidedAt,
    @Schema(nullable = true) CatalogItemResponse catalogItem) {

  static ProposalDecisionResponse of(ResolveProposalUseCase.Resolution resolution) {
    return new ProposalDecisionResponse(
        resolution.proposal().getId(),
        resolution.proposal().getStatus().name(),
        resolution.proposal().getResolvedAt(),
        resolution.item() == null ? null : CatalogItemResponse.of(resolution.item()));
  }
}
