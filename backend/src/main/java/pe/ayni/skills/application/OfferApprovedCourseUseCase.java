package pe.ayni.skills.application;

import java.time.Clock;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.ayni.identity.ApprovedCourseView;
import pe.ayni.identity.IdentityApi;
import pe.ayni.identity.TenantView;
import pe.ayni.shared.events.SkillEnabled;
import pe.ayni.shared.tenancy.TenantContext;
import pe.ayni.skills.CatalogScope;
import pe.ayni.skills.domain.model.CatalogItem;
import pe.ayni.skills.domain.model.OfferedSkill;
import pe.ayni.skills.domain.model.SkillsRuleViolation;
import pe.ayni.skills.infrastructure.CatalogItemRepository;
import pe.ayni.skills.infrastructure.OfferedSkillRepository;

/**
 * US13: offers a university course a tutor already passed, enabled the moment the grade clears
 * the university's threshold.
 *
 * <p>Only university courses go through this path: a global tool has no academic record to check
 * against, and reaches {@link pe.ayni.skills.domain.model.OfferedSkillStatus#ENABLED} only through
 * reviewed evidence, which this use case does not build.
 *
 * <p>{@code identity} is injected {@code @Lazy}: identity has no implementation yet, and without
 * it a plain injection would refuse to create this bean, which would in turn stop every other
 * module's Spring context from starting, including tests that have nothing to do with skills. The
 * proxy defers that lookup to the first call of {@link #execute}, so the rest of the application
 * boots normally and only offering a skill fails until identity exists. Drop {@code @Lazy} once it
 * does.
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
      @Lazy IdentityApi identity,
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
   * @throws SkillsRuleViolation when the item is not a university course, the tutor's record has
   *     no matching approved course, the grade does not reach the university's threshold, or the
   *     tutor already offers it
   */
  @Transactional
  public OfferedSkill execute(UUID tutorId, UUID catalogItemId) {
    Objects.requireNonNull(tutorId, "tutorId must not be null");
    Objects.requireNonNull(catalogItemId, "catalogItemId must not be null");

    String tenantId = TenantContext.require();

    // Checked before anything else reads or writes: uq_offered_skills_tutor_item would refuse the
    // insert anyway, but as a raw constraint violation instead of a message the student can act on.
    if (offeredSkills.findByTenantIdAndTutorIdAndCatalogItemId(tenantId, tutorId, catalogItemId).isPresent()) {
      throw new SkillsRuleViolation("this tutor already has an offer for this catalog item");
    }

    CatalogItem item =
        catalogItems
            .findByIdAndTenantVisibility(catalogItemId, tenantId)
            .orElseThrow(
                () -> new NoSuchElementException("catalog item %s not found".formatted(catalogItemId)));

    if (item.getScope() != CatalogScope.UNIVERSITY) {
      throw new SkillsRuleViolation(
          "global tools have no academic record; offer them through reviewed evidence instead");
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

    OfferedSkill skill =
        OfferedSkill.enableByAcademicRecord(
            UUID.randomUUID(),
            tenantId,
            tutorId,
            catalogItemId,
            approved.grade(),
            tenant.minimumTeachingGrade(),
            clock.instant());

    offeredSkills.save(skill);

    // Said out loud so that matching can pick it up, without skills knowing matching exists.
    events.publishEvent(new SkillEnabled(tenantId, tutorId, catalogItemId, clock.instant()));

    return skill;
  }
}
