package pe.ayni.skills.application;

import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.ayni.identity.IdentityApi;
import pe.ayni.identity.UserView;
import pe.ayni.shared.tenancy.TenantContext;
import pe.ayni.skills.domain.model.CatalogItem;
import pe.ayni.skills.domain.model.Category;
import pe.ayni.skills.domain.model.ProposalStatus;
import pe.ayni.skills.domain.model.SkillProposal;
import pe.ayni.skills.infrastructure.CatalogItemRepository;
import pe.ayni.skills.infrastructure.CategoryRepository;
import pe.ayni.skills.infrastructure.SkillProposalRepository;

/**
 * US43, scenarios 1 and 5: the moderator's view of the proposals of their university.
 *
 * <p>The queue is what still waits, oldest first, because a student has been waiting since then. The
 * history is what was already decided, newest first, and says who decided, when and how. Both are
 * the same read with another status. A page costs one read of the proposals, one of the categories
 * and one of the items, whatever its size; only the people are asked one by one, since identity
 * answers for a single user at a time.
 */
@Service
public class ModerationQuery {

  private final CoordinatorGuard moderators;
  private final SkillProposalRepository proposals;
  private final CategoryRepository categories;
  private final CatalogItemRepository catalogItems;
  private final SimilarItemsQuery similarItems;
  private final IdentityApi identity;

  ModerationQuery(
      CoordinatorGuard moderators,
      SkillProposalRepository proposals,
      CategoryRepository categories,
      CatalogItemRepository catalogItems,
      SimilarItemsQuery similarItems,
      IdentityApi identity) {
    this.moderators = moderators;
    this.proposals = proposals;
    this.categories = categories;
    this.catalogItems = catalogItems;
    this.similarItems = similarItems;
    this.identity = identity;
  }

  /**
   * A proposal with everything the moderator needs to read it.
   *
   * @param proposer the student who proposed it
   * @param resolver the moderator who decided, or {@code null} while it waits
   * @param catalogItem the item it ended up in, or {@code null} while it waits and for a rejection
   */
  public record ModerationItem(
      SkillProposal proposal,
      String categoryName,
      UserView proposer,
      UserView resolver,
      CatalogItem catalogItem) {}

  /**
   * One proposal, with the catalogue items it looks like, which is what a moderator needs to decide
   * whether to join it to one of them.
   *
   * @param similar empty once the proposal was resolved: there is nothing left to decide
   */
  public record ModerationDetail(ModerationItem item, List<CatalogItem> similar) {}

  /**
   * One page of the proposals of the university in the given status.
   *
   * @throws pe.ayni.skills.domain.model.NotACoordinator when the person asking is not a coordinator
   */
  @Transactional(readOnly = true)
  public Page<ModerationItem> page(UUID moderatorId, ProposalStatus status, int page, int size) {
    Objects.requireNonNull(status, "status must not be null");
    moderators.require(moderatorId);
    String tenantId = TenantContext.require();

    Sort sort =
        status == ProposalStatus.PROPOSED
            ? Sort.by("createdAt", "id")
            : Sort.by(Sort.Order.desc("resolvedAt"), Sort.Order.asc("id"));
    Page<SkillProposal> found =
        proposals.findByTenantIdAndStatus(tenantId, status, PageRequest.of(page, size, sort));
    if (found.isEmpty()) {
      return found.map(proposal -> null);
    }
    Map<UUID, ModerationItem> byId =
        describe(found.getContent()).stream()
            .collect(Collectors.toMap(item -> item.proposal().getId(), Function.identity()));
    return found.map(proposal -> byId.get(proposal.getId()));
  }

  /**
   * @throws NoSuchElementException when the proposal does not exist in this university
   * @throws pe.ayni.skills.domain.model.NotACoordinator when the person asking is not a coordinator
   */
  @Transactional(readOnly = true)
  public ModerationDetail detail(UUID moderatorId, UUID proposalId) {
    moderators.require(moderatorId);
    String tenantId = TenantContext.require();

    SkillProposal proposal =
        proposals
            .findByTenantIdAndId(tenantId, proposalId)
            .orElseThrow(() -> new NoSuchElementException("proposal " + proposalId + " does not exist"));
    ModerationItem item = describe(List.of(proposal)).get(0);
    List<CatalogItem> similar =
        proposal.isWaiting() ? similarItems.similarTo(proposal.getName()) : List.of();
    return new ModerationDetail(item, similar);
  }

  private List<ModerationItem> describe(List<SkillProposal> found) {
    Map<UUID, String> categoryNames =
        categories.findAllById(found.stream().map(SkillProposal::getCategoryId).distinct().toList()).stream()
            .collect(Collectors.toMap(Category::getId, Category::getName, (first, second) -> first));
    List<UUID> itemIds =
        found.stream().map(SkillProposal::getCatalogItemId).filter(Objects::nonNull).distinct().toList();
    Map<UUID, CatalogItem> items =
        itemIds.isEmpty()
            ? Map.of()
            : catalogItems.findAllById(itemIds).stream()
                .collect(Collectors.toMap(CatalogItem::getId, Function.identity()));
    Map<UUID, UserView> people =
        found.stream()
            .flatMap(proposal -> Stream.of(proposal.getProposedBy(), proposal.getResolvedBy()))
            .filter(Objects::nonNull)
            .distinct()
            .collect(Collectors.toMap(Function.identity(), identity::requireUser));

    return found.stream()
        .map(
            proposal ->
                new ModerationItem(
                    proposal,
                    categoryNames.get(proposal.getCategoryId()),
                    people.get(proposal.getProposedBy()),
                    proposal.getResolvedBy() == null ? null : people.get(proposal.getResolvedBy()),
                    proposal.getCatalogItemId() == null ? null : items.get(proposal.getCatalogItemId())))
        .toList();
  }
}
