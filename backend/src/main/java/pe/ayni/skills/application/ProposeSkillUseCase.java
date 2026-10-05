package pe.ayni.skills.application;

import java.time.Clock;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.ayni.shared.tenancy.TenantContext;
import pe.ayni.skills.domain.model.CatalogItem;
import pe.ayni.skills.domain.model.Category;
import pe.ayni.skills.domain.model.ProposalStatus;
import pe.ayni.skills.domain.model.SimilarSkillsFound;
import pe.ayni.skills.domain.model.SkillProposal;
import pe.ayni.skills.domain.model.SkillsRuleViolation;
import pe.ayni.skills.domain.model.SkillsStateConflict;
import pe.ayni.skills.infrastructure.CategoryRepository;
import pe.ayni.skills.infrastructure.SkillProposalRepository;

/**
 * US42, scenarios 1 and 2: a student who does not find a tool proposes it, once the catalogue has
 * shown them what looks like it.
 *
 * <p>Three things stop a proposal, each for its own reason:
 *
 * <ul>
 *   <li>The catalogue already has a skill with that very name. Nothing the student says changes it.
 *   <li>The student already has that proposal waiting.
 *   <li>The catalogue has skills that look like it, and the student has not confirmed that theirs is
 *       a different one. The same request goes through once they do.
 * </ul>
 */
@Service
public class ProposeSkillUseCase {

  private final SkillProposalRepository proposals;
  private final CategoryRepository categories;
  private final SimilarItemsQuery similarItems;
  private final Clock clock;

  ProposeSkillUseCase(
      SkillProposalRepository proposals,
      CategoryRepository categories,
      SimilarItemsQuery similarItems,
      Clock clock) {
    this.proposals = proposals;
    this.categories = categories;
    this.similarItems = similarItems;
    this.clock = clock;
  }

  /**
   * @param confirmDistinct that the student saw the similar skills and theirs is a different one
   * @throws NoSuchElementException when the category does not exist
   * @throws SkillsRuleViolation when the name or the description do not fit what the table holds
   * @throws SimilarSkillsFound when skills that look like it exist and it was not confirmed
   * @throws SkillsStateConflict when the catalogue has that name, or the student already waits on it
   */
  @Transactional
  public ProposalView execute(
      UUID proposerId, UUID categoryId, String name, String description, boolean confirmDistinct) {
    Objects.requireNonNull(proposerId, "proposerId must not be null");
    Objects.requireNonNull(categoryId, "categoryId must not be null");

    String tenantId = TenantContext.require();
    SkillProposal proposal =
        SkillProposal.propose(
            UUID.randomUUID(), tenantId, proposerId, categoryId, name, description, clock.instant());
    Category category =
        categories
            .findById(categoryId)
            .orElseThrow(() -> new NoSuchElementException("category " + categoryId + " does not exist"));

    List<CatalogItem> similar = similarItems.similarTo(proposal.getName());
    similar.stream()
        .filter(item -> NameSimilarity.isSameName(item.getName(), proposal.getName()))
        .findFirst()
        .ifPresent(
            item -> {
              throw new SkillsStateConflict(
                  "the catalogue already has \"%s\"; offer it from there".formatted(item.getName()));
            });
    if (proposals.existsByTenantIdAndProposedByAndStatusAndNameIgnoreCase(
        tenantId, proposerId, ProposalStatus.PROPOSED, proposal.getName())) {
      throw new SkillsStateConflict("you already proposed this skill and it waits for a moderator");
    }
    if (!similar.isEmpty() && !confirmDistinct) {
      throw new SimilarSkillsFound(similar);
    }

    try {
      proposals.saveAndFlush(proposal);
    } catch (DataIntegrityViolationException raced) {
      // The same proposal was received between the read and the insert.
      throw new SkillsStateConflict("you already proposed this skill and it waits for a moderator");
    }
    return new ProposalView(proposal, category.getName());
  }
}
