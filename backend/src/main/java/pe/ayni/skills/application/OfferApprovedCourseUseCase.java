package pe.ayni.skills.application;

import java.time.Clock;
import java.time.Instant;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.ayni.identity.ApprovedCourseView;
import pe.ayni.identity.IdentityApi;
import pe.ayni.identity.TenantView;
import pe.ayni.shared.events.SkillEnabled;
import pe.ayni.shared.tenancy.TenantContext;
import pe.ayni.skills.CatalogScope;
import pe.ayni.skills.domain.model.AccreditationPath;
import pe.ayni.skills.domain.model.CatalogItem;
import pe.ayni.skills.domain.model.OfferedSkill;
import pe.ayni.skills.domain.model.OfferedSkillStatus;
import pe.ayni.skills.domain.model.SkillsRuleViolation;
import pe.ayni.skills.domain.model.SkillsStateConflict;
import pe.ayni.skills.infrastructure.CatalogItemRepository;
import pe.ayni.skills.infrastructure.OfferedSkillRepository;

/**
 * US13: offers a university course a tutor already passed, enabled the moment the grade clears
 * the university's threshold.
 *
 * <p>Only university courses are enabled here: a global tool has no academic record to check
 * against, and reaches {@link pe.ayni.skills.domain.model.OfferedSkillStatus#ENABLED} through
 * reviewed evidence (US16). The one thing this use case does for a tool is offer it again after
 * the tutor withdrew it, since a coordinator already accepted the evidence.
 *
 * <p>A course the tutor withdrew (US18) can be offered again. The same row comes back to life,
 * because a tutor holds one row per item, and the grade is checked once more against the threshold
 * in force today.
 */
@Service
public class OfferApprovedCourseUseCase {

  private final CatalogItemRepository catalogItems;
  private final OfferedSkillRepository offeredSkills;
  private final IdentityApi identity;
  private final ApplicationEventPublisher events;
  private final Clock clock;

  OfferApprovedCourseUseCase(
      CatalogItemRepository catalogItems,
      OfferedSkillRepository offeredSkills,
      IdentityApi identity,
      ApplicationEventPublisher events,
      Clock clock) {
    this.catalogItems = catalogItems;
    this.offeredSkills = offeredSkills;
    this.identity = identity;
    this.events = events;
    this.clock = clock;
  }

  /**
   * @throws NoSuchElementException when the item does not exist or is not visible to this tenant
   * @throws SkillsStateConflict when the tutor already offers the item
   * @throws SkillsRuleViolation when the item is retired or not a university course, the tutor's
   *     record has no matching approved course, or the grade does not reach the university's
   *     threshold
   */
  // Every refusal is decided before anything is written, so a refusal leaves nothing to undo. Not
  // rolling back for it lets a caller in the same transaction, such as US40's onboarding, skip the
  // item and go on: otherwise Spring marks the whole transaction rollback-only and the caller's
  // commit fails even though it caught the refusal.
  @Transactional(noRollbackFor = SkillsRuleViolation.class)
  public OfferedSkill execute(UUID tutorId, UUID catalogItemId) {
    Objects.requireNonNull(tutorId, "tutorId must not be null");
    Objects.requireNonNull(catalogItemId, "catalogItemId must not be null");

    String tenantId = TenantContext.require();

    // Checked before anything else reads or writes: uq_offered_skills_tutor_item would refuse the
    // insert anyway, but as a raw constraint violation instead of a message the student can act on.
    // A withdrawn offer is the one exception: it is offered again below.
    Optional<OfferedSkill> existing =
        offeredSkills.findByTenantIdAndTutorIdAndCatalogItemId(tenantId, tutorId, catalogItemId);
    if (existing.isPresent() && existing.get().getStatus() != OfferedSkillStatus.WITHDRAWN) {
      throw new SkillsStateConflict("this tutor already has an offer for this catalog item");
    }

    CatalogItem item =
        catalogItems
            .findByIdAndTenantVisibility(catalogItemId, tenantId)
            .orElseThrow(
                () -> new NoSuchElementException("catalog item %s not found".formatted(catalogItemId)));

    if (!item.isActive()) {
      throw new SkillsRuleViolation("this catalogue item is retired and can no longer be offered");
    }

    if (item.getScope() != CatalogScope.UNIVERSITY) {
      return offerGlobalToolAgain(existing, tenantId, tutorId, catalogItemId);
    }

    ApprovedCourseView approved =
        identity.approvedCourses(tutorId).stream()
            .filter(course -> course.courseCode().equals(item.getCourseCode()))
            .findFirst()
            .orElseThrow(
                () ->
                    new SkillsRuleViolation(
                        "the tutor's academic record has no approved course %s"
                            .formatted(item.getCourseCode())));

    TenantView tenant = identity.requireTenant(tenantId);
    Instant now = clock.instant();

    OfferedSkill skill;
    if (existing.isPresent()) {
      skill = existing.get();
      skill.reEnableByAcademicRecord(approved.grade(), tenant.minimumTeachingGrade(), now);
    } else {
      skill =
          OfferedSkill.enableByAcademicRecord(
              UUID.randomUUID(),
              tenantId,
              tutorId,
              catalogItemId,
              approved.grade(),
              tenant.minimumTeachingGrade(),
              now);
    }
    offeredSkills.save(skill);

    // Said out loud so that matching can pick it up, without skills knowing matching exists.
    events.publishEvent(new SkillEnabled(tenantId, tutorId, catalogItemId, now));

    return skill;
  }

  /**
   * A global tool has no academic record to check, so it is offered here only in one case: the tutor
   * withdrew it after a coordinator accepted their evidence, which is still valid. Any other tool
   * goes through the submission of evidence.
   *
   * <p>Reached only when the tutor holds no skill for the tool, or a withdrawn one: anything else
   * was refused before as a conflict.
   */
  private OfferedSkill offerGlobalToolAgain(
      Optional<OfferedSkill> existing, String tenantId, UUID tutorId, UUID catalogItemId) {
    OfferedSkill skill =
        existing
            .filter(withdrawn -> withdrawn.getAccreditationPath() == AccreditationPath.REVIEWED_EVIDENCE)
            .orElseThrow(
                () ->
                    new SkillsRuleViolation(
                        "global tools have no academic record; offer them through reviewed evidence"
                            + " instead"));
    Instant now = clock.instant();
    skill.reEnableByReviewedEvidence(now);
    offeredSkills.save(skill);
    events.publishEvent(new SkillEnabled(tenantId, tutorId, catalogItemId, now));
    return skill;
  }
}
