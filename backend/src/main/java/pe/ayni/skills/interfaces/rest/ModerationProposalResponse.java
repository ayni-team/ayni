package pe.ayni.skills.interfaces.rest;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;
import pe.ayni.skills.application.ModerationQuery.ModerationItem;
import pe.ayni.skills.domain.model.SkillProposal;

/**
 * A proposal as the moderator reads it: what was proposed, by whom, since when, and, once it was
 * resolved, who decided, when and how.
 *
 * @param resolution {@code null} while the proposal waits
 */
@Schema(name = "ModerationProposal", description = "A tool a student proposed, for the moderator")
public record ModerationProposalResponse(
    @Schema(example = "c0000000-0000-4000-8000-000000000001") UUID id,
    @Schema(example = "Figma") String name,
    @Schema(nullable = true) String description,
    Category category,
    @Schema(allowableValues = {"PROPOSED", "APPROVED", "MERGED", "REJECTED"}, example = "PROPOSED")
        String status,
    @Schema(description = "Since when it waits", example = "2026-10-04T15:30:00Z") Instant proposedAt,
    Proposer proposer,
    @Schema(nullable = true) Resolution resolution) {

  /** The category the student chose. */
  @Schema(name = "ModerationProposalCategory")
  public record Category(
      @Schema(example = "b0000000-0000-4000-8000-000000000001") UUID id,
      @Schema(example = "Design") String name) {}

  /** The student who proposed it, as the coordinator of their university may see them. */
  @Schema(name = "ModerationProposalProposer")
  public record Proposer(
      @Schema(example = "11111111-1111-4111-8111-111111111111") UUID id,
      @Schema(example = "Ana Torres") String fullName,
      @Schema(example = "U202310949") String studentCode,
      @Schema(example = "Software Engineering") String career) {}

  /**
   * How it was decided.
   *
   * @param how APPROVED, MERGED or REJECTED
   * @param reason why; {@code null} when none was given, and never null for a rejection
   * @param catalogItem the item it ended up in; {@code null} for a rejection
   */
  @Schema(name = "ModerationProposalResolution")
  public record Resolution(
      @Schema(allowableValues = {"APPROVED", "MERGED", "REJECTED"}, example = "APPROVED") String how,
      ResolvedBy resolvedBy,
      @Schema(example = "2026-10-05T09:00:00Z") Instant resolvedAt,
      @Schema(nullable = true) String reason,
      @Schema(nullable = true) CatalogItemRef catalogItem) {}

  /** The moderator who decided. */
  @Schema(name = "ModerationProposalResolvedBy")
  public record ResolvedBy(
      @Schema(example = "22222222-2222-4222-8222-222222222222") UUID id,
      @Schema(example = "Carla Ríos") String fullName) {}

  /** The catalogue item the proposal ended up in. */
  @Schema(name = "ModerationProposalCatalogItem")
  public record CatalogItemRef(
      @Schema(example = "b0000000-0000-4000-8000-000000000101") UUID id,
      @Schema(example = "Figma") String name,
      @Schema(example = "GLOBAL") String scope) {}

  static ModerationProposalResponse of(ModerationItem item) {
    SkillProposal proposal = item.proposal();
    Resolution resolution =
        proposal.isWaiting()
            ? null
            : new Resolution(
                proposal.getStatus().name(),
                new ResolvedBy(proposal.getResolvedBy(), item.resolver().fullName()),
                proposal.getResolvedAt(),
                proposal.getDecisionReason(),
                item.catalogItem() == null
                    ? null
                    : new CatalogItemRef(
                        item.catalogItem().getId(),
                        item.catalogItem().getName(),
                        item.catalogItem().getScope().name()));
    return new ModerationProposalResponse(
        proposal.getId(),
        proposal.getName(),
        proposal.getDescription(),
        new Category(proposal.getCategoryId(), item.categoryName()),
        proposal.getStatus().name(),
        proposal.getCreatedAt(),
        new Proposer(
            item.proposer().id(),
            item.proposer().fullName(),
            item.proposer().studentCode(),
            item.proposer().career()),
        resolution);
  }
}
