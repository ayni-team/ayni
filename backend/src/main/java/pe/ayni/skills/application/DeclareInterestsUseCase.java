package pe.ayni.skills.application;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.ayni.shared.tenancy.TenantContext;
import pe.ayni.skills.domain.model.CatalogItem;
import pe.ayni.skills.domain.model.LearningInterest;
import pe.ayni.skills.domain.model.OfferedSkill;
import pe.ayni.skills.domain.model.SkillsRuleViolation;
import pe.ayni.skills.infrastructure.CatalogItemRepository;
import pe.ayni.skills.infrastructure.LearningInterestRepository;

/**
 * US40: the two halves of a student's initial configuration that belong to skills.
 *
 * <p>"Need help with" registers an interest of its own, {@link LearningInterest}. "Can teach"
 * declares no interest: it goes straight through {@link OfferApprovedCourseUseCase}, the same
 * academic-record path US13 built, since offering a course during onboarding is not a different
 * operation from offering it from its own screen.
 */
@Service
public class DeclareInterestsUseCase {

  private final LearningInterestRepository learningInterests;
  private final CatalogItemRepository catalogItems;
  private final OfferApprovedCourseUseCase offerApprovedCourse;
  private final Clock clock;

  DeclareInterestsUseCase(
      LearningInterestRepository learningInterests,
      CatalogItemRepository catalogItems,
      OfferApprovedCourseUseCase offerApprovedCourse,
      Clock clock) {
    this.learningInterests = learningInterests;
    this.catalogItems = catalogItems;
    this.offerApprovedCourse = offerApprovedCourse;
    this.clock = clock;
  }

  /**
   * Registers the items as courses the student needs help with. Repeats are harmless.
   *
   * <p>Every item must be visible to the student's university: the foreign key only proves the item
   * exists, and would accept a course of another university.
   *
   * @throws NoSuchElementException when an item does not exist or belongs to another university
   */
  @Transactional
  public List<LearningInterest> declareLearningInterests(UUID studentId, List<UUID> catalogItemIds) {
    Objects.requireNonNull(studentId, "studentId must not be null");
    Objects.requireNonNull(catalogItemIds, "catalogItemIds must not be null");

    String tenantId = TenantContext.require();
    Instant now = clock.instant();
    Set<UUID> requested = new LinkedHashSet<>(catalogItemIds);

    Set<UUID> visible =
        catalogItems.findAllById(requested).stream()
            .filter(item -> item.isVisibleTo(tenantId))
            .map(CatalogItem::getId)
            .collect(Collectors.toSet());
    for (UUID catalogItemId : requested) {
      if (!visible.contains(catalogItemId)) {
        throw new NoSuchElementException("catalog item %s not found".formatted(catalogItemId));
      }
    }

    // One read of what the student already declared, instead of one per item.
    Map<UUID, LearningInterest> declared =
        learningInterests.findByTenantIdAndStudentId(tenantId, studentId).stream()
            .collect(Collectors.toMap(LearningInterest::getCatalogItemId, Function.identity()));

    List<LearningInterest> result = new ArrayList<>(requested.size());
    for (UUID catalogItemId : requested) {
      LearningInterest interest = declared.get(catalogItemId);
      if (interest == null) {
        interest =
            learningInterests.save(
                new LearningInterest(UUID.randomUUID(), tenantId, studentId, catalogItemId, now));
      }
      result.add(interest);
    }
    return result;
  }

  /**
   * Enables, by academic record, every item among these the student's approved courses already
   * clear.
   *
   * <p>An item already offered, not backed by an approved course, or not a university course at
   * all is skipped rather than refused: US40 scenario 5 lets a student resume an interrupted
   * configuration, and a second submission of the same choices must not fail on what the first one
   * already did.
   */
  @Transactional
  public List<OfferedSkill> declareTeachingInterests(UUID studentId, List<UUID> catalogItemIds) {
    Objects.requireNonNull(studentId, "studentId must not be null");
    Objects.requireNonNull(catalogItemIds, "catalogItemIds must not be null");

    List<OfferedSkill> enabled = new ArrayList<>(catalogItemIds.size());
    for (UUID catalogItemId : catalogItemIds) {
      try {
        enabled.add(offerApprovedCourse.execute(studentId, catalogItemId));
      } catch (SkillsRuleViolation skipped) {
        // Already offered, not approved, or not a course: none of these are worth failing the
        // whole step over, since the student chose this list without knowing which items qualify.
      }
    }
    return enabled;
  }
}
