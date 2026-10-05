package pe.ayni.skills.interfaces.rest;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;
import pe.ayni.skills.application.ProposalView;
import pe.ayni.skills.domain.model.SkillProposal;

/**
 * A proposal as its author sees it. Who decided is not shown: the student reads the decision, not the
 * moderator.
 *
 * @param decisionReason why it was refused; {@code null} while it waits and when it was approved
 *     without one
 * @param resolvedAt when it was decided; {@code null} while it waits
 * @param catalogItemId the catalogue item an approval created; {@code null} until then
 */
@Schema(name = "SkillProposal", description = "A tool the student proposed, and where it stands")
public record SkillProposalResponse(
    @Schema(example = "c0000000-0000-4000-8000-000000000001") UUID id,
    @Schema(example = "Figma") String name,
    @Schema(nullable = true) String description,
    @Schema(example = "b0000000-0000-4000-8000-000000000001") UUID categoryId,
    @Schema(example = "Design") String categoryName,
    @Schema(allowableValues = {"PROPOSED", "APPROVED", "REJECTED"}, example = "PROPOSED")
        String status,
    @Schema(nullable = true) String decisionReason,
    @Schema(nullable = true) UUID catalogItemId,
    Instant createdAt,
    @Schema(nullable = true) Instant resolvedAt) {

  static SkillProposalResponse of(ProposalView view) {
    SkillProposal proposal = view.proposal();
    return new SkillProposalResponse(
        proposal.getId(),
        proposal.getName(),
        proposal.getDescription(),
        proposal.getCategoryId(),
        view.categoryName(),
        proposal.getStatus().name(),
        proposal.getDecisionReason(),
        proposal.getCatalogItemId(),
        proposal.getCreatedAt(),
        proposal.getResolvedAt());
  }
}
