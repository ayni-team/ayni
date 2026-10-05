package pe.ayni.skills.application;

import java.time.Clock;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.ayni.shared.events.SkillEnabled;
import pe.ayni.shared.events.SkillWithdrawn;
import pe.ayni.shared.tenancy.TenantContext;
import pe.ayni.skills.domain.model.CatalogItem;
import pe.ayni.skills.domain.model.OfferedSkill;
import pe.ayni.skills.domain.model.SkillsRuleViolation;
import pe.ayni.skills.domain.model.SkillsStateConflict;
import pe.ayni.skills.infrastructure.CatalogItemRepository;
import pe.ayni.skills.infrastructure.LearningInterestRepository;
import pe.ayni.skills.infrastructure.OfferedSkillRepository;
import pe.ayni.skills.infrastructure.SkillProposalRepository;
import pe.ayni.skills.infrastructure.TaughtSessionRepository;

/**
 * US44, scenario 1: a moderator finds that two catalogue items are the same skill and joins them, so
 * that students stop choosing between three versions of one thing.
 *
 * <p>The item that stays keeps everything the other one had:
 *
 * <ul>
 *   <li>The offers and accreditations of the tutors who held the other item move to the one that
 *       stays, with their status and how they were earned. A tutor who is enabled keeps being found
 *       in the search: the search is told the old offer ended and the new one started.
 *   <li>A tutor who already held both keeps the offer on the item that stays, and the one on the
 *       other item is withdrawn, since two offers of one skill make no sense.
 *   <li>The learning interests, the proposals that ended in the other item and the sessions counted
 *       on it follow to the one that stays.
 * </ul>
 *
 * <p>The other item is retired. The sessions already booked on it stand: booking is not touched, and
 * the history of ratings and bookings stays with the retired item.
 *
 * <p>The two must be the same kind of thing: two tools, or two courses of this university. A course
 * is accredited by an academic record under its code, so it is never joined to a tool.
 */
@Service
public class MergeCatalogItemsUseCase {

  private final CoordinatorGuard moderators;
  private final CatalogItemRepository catalogItems;
  private final OfferedSkillRepository offeredSkills;
  private final LearningInterestRepository learningInterests;
  private final SkillProposalRepository proposals;
  private final TaughtSessionRepository taughtSessions;
  private final ApplicationEventPublisher events;
  private final Clock clock;

  MergeCatalogItemsUseCase(
      CoordinatorGuard moderators,
      CatalogItemRepository catalogItems,
      OfferedSkillRepository offeredSkills,
      LearningInterestRepository learningInterests,
      SkillProposalRepository proposals,
      TaughtSessionRepository taughtSessions,
      ApplicationEventPublisher events,
      Clock clock) {
    this.moderators = moderators;
    this.catalogItems = catalogItems;
    this.offeredSkills = offeredSkills;
    this.learningInterests = learningInterests;
    this.proposals = proposals;
    this.taughtSessions = taughtSessions;
    this.events = events;
    this.clock = clock;
  }

  /**
   * What was joined.
   *
   * @param offersMoved offers that moved to the item that stays, whatever their status
   * @param offersWithdrawn enabled offers of tutors who already held the item that stays
   * @param interestsMoved learning interests that moved
   * @param interestsDropped learning interests of students who already had one in the item that stays
   */
  public record Merge(
      CatalogItem removed,
      CatalogItem kept,
      long offersMoved,
      long offersWithdrawn,
      long interestsMoved,
      long interestsDropped,
      long proposalsRepointed,
      long sessionsCarriedOver) {}

  /**
   * @param removedId the duplicate, which is retired
   * @param keptId the item that stays
   * @throws pe.ayni.skills.domain.model.NotACoordinator when the person is not a coordinator
   * @throws NoSuchElementException when either item does not exist, or is a course of another
   *     university
   * @throws SkillsRuleViolation when they are the same item, not the same kind of thing, or the one
   *     that would stay is retired
   * @throws SkillsStateConflict when the one to remove is already retired
   */
  @Transactional
  public Merge execute(UUID moderatorId, UUID removedId, UUID keptId) {
    Objects.requireNonNull(moderatorId, "moderatorId must not be null");
    Objects.requireNonNull(removedId, "removedId must not be null");
    Objects.requireNonNull(keptId, "keptId must not be null");
    if (removedId.equals(keptId)) {
      throw new SkillsRuleViolation("an item cannot be joined to itself");
    }

    moderators.require(moderatorId);
    String tenantId = TenantContext.require();

    // Locked in the same order whoever asks, so two moderators joining the same pair in opposite
    // directions wait for each other instead of each holding one.
    UUID first = removedId.compareTo(keptId) < 0 ? removedId : keptId;
    UUID second = first.equals(removedId) ? keptId : removedId;
    CatalogItem lockedFirst = lock(first, tenantId);
    CatalogItem lockedSecond = lock(second, tenantId);
    CatalogItem removed = first.equals(removedId) ? lockedFirst : lockedSecond;
    CatalogItem kept = first.equals(removedId) ? lockedSecond : lockedFirst;

    if (removed.getScope() != kept.getScope()) {
      throw new SkillsRuleViolation("a course and a tool are not the same skill and cannot be joined");
    }
    if (!removed.isActive()) {
      throw new SkillsStateConflict("the item to remove is already retired");
    }
    if (!kept.isActive()) {
      throw new SkillsRuleViolation("the item that would stay was retired from the catalogue");
    }

    Instant now = clock.instant();
    long[] offers = moveOffers(removed, kept, now);
    removed.retire();
    catalogItems.flush();

    int interestsDropped = learningInterests.deleteDuplicatesOf(removed.getId(), kept.getId());
    int interestsMoved = learningInterests.moveInterests(removed.getId(), kept.getId());
    int proposalsRepointed = proposals.repoint(removed.getId(), kept.getId());
    int sessionsCarriedOver = taughtSessions.carryOver(removed.getId(), kept.getId());

    return new Merge(
        removed, kept, offers[0], offers[1], interestsMoved, interestsDropped, proposalsRepointed, sessionsCarriedOver);
  }

  private CatalogItem lock(UUID itemId, String tenantId) {
    return catalogItems
        .lockByIdAndTenantVisibility(itemId, tenantId)
        .orElseThrow(() -> new NoSuchElementException("catalogue item %s not found".formatted(itemId)));
  }

  /** @return how many offers moved and how many were withdrawn instead */
  private long[] moveOffers(CatalogItem removed, CatalogItem kept, Instant now) {
    List<OfferedSkill> held = offeredSkills.lockByCatalogItemId(removed.getId());
    Set<String> alreadyOnKept = new HashSet<>();
    for (OfferedSkill skill : offeredSkills.lockByCatalogItemId(kept.getId())) {
      alreadyOnKept.add(holder(skill));
    }

    long moved = 0;
    long withdrawn = 0;
    for (OfferedSkill skill : held) {
      boolean enabled = skill.isEnabled();
      if (alreadyOnKept.contains(holder(skill))) {
        if (enabled) {
          skill.withdraw(now);
          events.publishEvent(new SkillWithdrawn(skill.getTenantId(), skill.getTutorId(), removed.getId(), now));
          withdrawn++;
        }
        continue;
      }
      skill.moveTo(kept.getId(), now);
      moved++;
      if (enabled) {
        // The search offers a tutor per skill: the old offer ends and the new one starts.
        events.publishEvent(new SkillWithdrawn(skill.getTenantId(), skill.getTutorId(), removed.getId(), now));
        events.publishEvent(new SkillEnabled(skill.getTenantId(), skill.getTutorId(), kept.getId(), now));
      }
    }
    return new long[] {moved, withdrawn};
  }

  /** A tutor is the pair of their university and themselves: the same person never holds an item twice. */
  private static String holder(OfferedSkill skill) {
    return skill.getTenantId() + "/" + skill.getTutorId();
  }
}
