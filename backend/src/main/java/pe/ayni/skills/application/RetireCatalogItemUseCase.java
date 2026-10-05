package pe.ayni.skills.application;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.ayni.shared.events.SkillWithdrawn;
import pe.ayni.shared.tenancy.TenantContext;
import pe.ayni.skills.domain.model.CatalogItem;
import pe.ayni.skills.domain.model.OfferedSkill;
import pe.ayni.skills.domain.model.OfferedSkillStatus;
import pe.ayni.skills.domain.model.SkillsRuleViolation;
import pe.ayni.skills.domain.model.SkillsStateConflict;
import pe.ayni.skills.infrastructure.CatalogItemRepository;
import pe.ayni.skills.infrastructure.OfferedSkillRepository;

/**
 * US44, scenarios 2 and 3: a moderator retires a catalogue item that no longer belongs.
 *
 * <p>A retired item can no longer be offered, and the search stops finding it. Every tutor who
 * offered it has their offer withdrawn, which announces {@code SkillWithdrawn} for each one: that is
 * what takes the item out of the search projection. The sessions already booked stand, because
 * nothing here touches booking.
 *
 * <p>The moderator is told how many tutors are affected before confirming, and confirms by sending
 * that number back. If it changed in the meantime, because a tutor started or stopped offering the
 * item, nothing is retired and the answer carries the number as it is now: the moderator decided
 * about something that is no longer true.
 *
 * <p>Offers that still wait for a review are not counted and not touched. They can no longer be
 * approved, and the coordinator rejects them.
 */
@Service
public class RetireCatalogItemUseCase {

  private final CoordinatorGuard moderators;
  private final CatalogItemRepository catalogItems;
  private final OfferedSkillRepository offeredSkills;
  private final ApplicationEventPublisher events;
  private final Clock clock;

  RetireCatalogItemUseCase(
      CoordinatorGuard moderators,
      CatalogItemRepository catalogItems,
      OfferedSkillRepository offeredSkills,
      ApplicationEventPublisher events,
      Clock clock) {
    this.moderators = moderators;
    this.catalogItems = catalogItems;
    this.offeredSkills = offeredSkills;
    this.events = events;
    this.clock = clock;
  }

  /**
   * What was retired.
   *
   * @param tutorsAffected tutors whose offer was withdrawn
   */
  public record Retirement(CatalogItem item, long tutorsAffected, Instant retiredAt) {}

  /**
   * @param confirmedTutors the number of tutors the moderator saw and agreed to affect
   * @throws pe.ayni.skills.domain.model.NotACoordinator when the person is not a coordinator
   * @throws NoSuchElementException when the item does not exist, or is a course of another
   *     university
   * @throws SkillsStateConflict when the item is already retired, or the number of tutors is no
   *     longer the one confirmed
   * @throws SkillsRuleViolation when the confirmation is negative
   */
  @Transactional
  public Retirement execute(UUID moderatorId, UUID catalogItemId, long confirmedTutors) {
    Objects.requireNonNull(moderatorId, "moderatorId must not be null");
    Objects.requireNonNull(catalogItemId, "catalogItemId must not be null");
    if (confirmedTutors < 0) {
      throw new SkillsRuleViolation("the number of tutors to confirm cannot be negative");
    }

    moderators.require(moderatorId);
    String tenantId = TenantContext.require();

    // Locked: nobody starts offering the item while it is being retired, and two moderators
    // retiring it at once do not both announce the withdrawals.
    CatalogItem item =
        catalogItems
            .lockByIdAndTenantVisibility(catalogItemId, tenantId)
            .orElseThrow(() -> new NoSuchElementException("catalogue item %s not found".formatted(catalogItemId)));
    if (!item.isActive()) {
      throw new SkillsStateConflict("this catalogue item is already retired");
    }
    List<OfferedSkill> offers =
        offeredSkills.lockByCatalogItemIdAndStatus(item.getId(), OfferedSkillStatus.ENABLED);
    if (offers.size() != confirmedTutors) {
      throw new SkillsStateConflict(
          "%d tutors offer this skill now, not %d: review the number and confirm again"
              .formatted(offers.size(), confirmedTutors));
    }

    Instant now = clock.instant();
    item.retire();
    for (OfferedSkill offer : offers) {
      offer.withdraw(now);
      events.publishEvent(new SkillWithdrawn(offer.getTenantId(), offer.getTutorId(), item.getId(), now));
    }
    return new Retirement(item, offers.size(), now);
  }
}
