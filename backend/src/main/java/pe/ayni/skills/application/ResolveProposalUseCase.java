package pe.ayni.skills.application;

import java.time.Clock;
import java.time.Instant;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.ayni.shared.tenancy.TenantContext;
import pe.ayni.skills.CatalogScope;
import pe.ayni.skills.domain.model.CatalogItem;
import pe.ayni.skills.domain.model.SkillProposal;
import pe.ayni.skills.domain.model.SkillsRuleViolation;
import pe.ayni.skills.domain.model.SkillsStateConflict;
import pe.ayni.skills.infrastructure.CatalogItemRepository;
import pe.ayni.skills.infrastructure.SkillProposalRepository;

/**
 * US43, scenarios 2, 3 and 4: a moderator approves a proposal, joins it to a skill the catalogue
 * already has, or rejects it.
 *
 * <ul>
 *   <li>Approving adds the tool to the catalogue as a global item, available to every university.
 *       The name must be free: if the catalogue already has it, the way out is to join the proposal
 *       to that item.
 *   <li>Joining creates nothing. The proposal keeps the item it was joined to, which the student
 *       reads to offer that skill or to submit evidence for it.
 *   <li>Rejecting needs a reason, because the student reads it.
 * </ul>
 *
 * <p>The proposal keeps who decided, when and how, which is the history the moderator reads.
 */
@Service
public class ResolveProposalUseCase {

  /** What the moderator decides. */
  public enum Decision {
    APPROVE,
    MERGE,
    REJECT
  }

  /**
   * What was decided.
   *
   * @param item the item the proposal ended up in; {@code null} for a rejection
   */
  public record Resolution(SkillProposal proposal, CatalogItem item) {}

  private final CoordinatorGuard moderators;
  private final SkillProposalRepository proposals;
  private final CatalogItemRepository catalogItems;
  private final SimilarItemsQuery similarItems;
  private final Clock clock;

  ResolveProposalUseCase(
      CoordinatorGuard moderators,
      SkillProposalRepository proposals,
      CatalogItemRepository catalogItems,
      SimilarItemsQuery similarItems,
      Clock clock) {
    this.moderators = moderators;
    this.proposals = proposals;
    this.catalogItems = catalogItems;
    this.similarItems = similarItems;
    this.clock = clock;
  }

  /**
   * @param catalogItemId the existing item to join the proposal to; required to merge and refused
   *     otherwise
   * @param reason why; required to reject, optional otherwise
   * @throws pe.ayni.skills.domain.model.NotACoordinator when the person is not a coordinator
   * @throws NoSuchElementException when the proposal, or the item to join it to, is not visible in
   *     this university
   * @throws SkillsStateConflict when the proposal was already resolved, or approving it would repeat
   *     a name the catalogue already has
   * @throws SkillsRuleViolation when a rejection has no reason, a merge no item, an item comes with
   *     another decision, or the item to join is retired
   */
  @Transactional
  public Resolution execute(
      UUID moderatorId, UUID proposalId, Decision decision, UUID catalogItemId, String reason) {
    Objects.requireNonNull(moderatorId, "moderatorId must not be null");
    Objects.requireNonNull(proposalId, "proposalId must not be null");
    Objects.requireNonNull(decision, "decision must not be null");

    moderators.require(moderatorId);
    String tenantId = TenantContext.require();
    requireItemOnlyToMerge(decision, catalogItemId);

    // Locked: two moderators on the same proposal must not both resolve it.
    SkillProposal proposal =
        proposals
            .lockByTenantIdAndId(tenantId, proposalId)
            .orElseThrow(() -> new NoSuchElementException("proposal %s not found".formatted(proposalId)));
    Instant now = clock.instant();

    return switch (decision) {
      case APPROVE -> approve(moderatorId, proposal, reason, now);
      case MERGE -> merge(moderatorId, proposal, catalogItemId, reason, tenantId, now);
      case REJECT -> {
        proposal.reject(moderatorId, reason, now);
        yield new Resolution(proposal, null);
      }
    };
  }

  private Resolution approve(UUID moderatorId, SkillProposal proposal, String reason, Instant now) {
    // Only the status is checked here, before the lock below is taken, so a proposal that was
    // already resolved never waits for anyone. The proposal itself checks again when it resolves.
    if (!proposal.isWaiting()) {
      throw new SkillsStateConflict("this proposal was already resolved");
    }
    catalogItems.lockGlobalCatalogue();
    similarItems.similarTo(proposal.getName()).stream()
        .filter(item -> NameSimilarity.isSameName(item.getName(), proposal.getName()))
        .findFirst()
        .ifPresent(
            item -> {
              throw new SkillsStateConflict(
                  "the catalogue already has \"%s\"; join the proposal to it instead".formatted(item.getName()));
            });

    CatalogItem created =
        catalogItems.saveAndFlush(
            new CatalogItem(
                UUID.randomUUID(),
                CatalogScope.GLOBAL,
                null,
                proposal.getCategoryId(),
                proposal.getName(),
                proposal.getDescription(),
                null,
                now));
    proposal.approve(moderatorId, created.getId(), reason, now);
    return new Resolution(proposal, created);
  }

  private Resolution merge(
      UUID moderatorId,
      SkillProposal proposal,
      UUID catalogItemId,
      String reason,
      String tenantId,
      Instant now) {
    if (catalogItemId == null) {
      throw new SkillsRuleViolation("choose the skill of the catalogue to join the proposal to");
    }
    CatalogItem existing =
        catalogItems
            .findByIdAndTenantVisibility(catalogItemId, tenantId)
            .orElseThrow(
                () -> new NoSuchElementException("catalogue item %s not found".formatted(catalogItemId)));
    if (!existing.isActive()) {
      throw new SkillsRuleViolation("the skill \"%s\" was retired from the catalogue".formatted(existing.getName()));
    }
    proposal.mergeInto(moderatorId, existing.getId(), reason, now);
    return new Resolution(proposal, existing);
  }

  private static void requireItemOnlyToMerge(Decision decision, UUID catalogItemId) {
    if (decision != Decision.MERGE && catalogItemId != null) {
      throw new SkillsRuleViolation("a catalogue item goes only with a decision to join the proposal to it");
    }
  }
}
