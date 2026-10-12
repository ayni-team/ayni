package pe.ayni.skills.application;

import java.time.Clock;
import java.time.Instant;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.ayni.shared.events.SkillWithdrawn;
import pe.ayni.shared.tenancy.TenantContext;
import pe.ayni.skills.domain.model.NotTheOwner;
import pe.ayni.skills.domain.model.OfferedSkill;
import pe.ayni.skills.domain.model.SkillsStateConflict;
import pe.ayni.skills.infrastructure.OfferedSkillRepository;

/**
 * US18, scenario 5: a tutor stops offering a skill.
 *
 * <p>Nothing here touches the bookings the tutor already has: they stand. The event is what takes
 * the course out of the search, and it is published here because nobody else can know that a skill
 * was withdrawn.
 */
@Service
public class WithdrawSkillUseCase {

  private final OfferedSkillRepository offeredSkills;
  private final ApplicationEventPublisher events;
  private final Clock clock;

  WithdrawSkillUseCase(
      OfferedSkillRepository offeredSkills, ApplicationEventPublisher events, Clock clock) {
    this.offeredSkills = offeredSkills;
    this.events = events;
    this.clock = clock;
  }

  /**
   * @throws NoSuchElementException when no skill has that identifier in this university
   * @throws NotTheOwner when the skill belongs to another tutor
   * @throws SkillsStateConflict when the skill is not enabled, which includes one already withdrawn
   */
  @Transactional
  public void execute(UUID tutorId, UUID offeredSkillId) {
    Objects.requireNonNull(tutorId, "tutorId must not be null");
    Objects.requireNonNull(offeredSkillId, "offeredSkillId must not be null");

    String tenantId = TenantContext.require();

    // Locked: two requests withdrawing the same skill at once must not both announce it.
    OfferedSkill skill =
        offeredSkills
            .lockByTenantIdAndId(tenantId, offeredSkillId)
            .orElseThrow(
                () -> new NoSuchElementException("skill %s not found".formatted(offeredSkillId)));

    if (!skill.getTutorId().equals(tutorId)) {
      throw new NotTheOwner();
    }

    Instant now = clock.instant();
    skill.withdraw(now);

    events.publishEvent(new SkillWithdrawn(tenantId, tutorId, skill.getCatalogItemId(), now));
  }
}
