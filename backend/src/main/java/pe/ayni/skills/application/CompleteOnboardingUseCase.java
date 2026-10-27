package pe.ayni.skills.application;

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
import pe.ayni.identity.IdentityApi;
import pe.ayni.identity.UserRole;
import pe.ayni.shared.tenancy.TenantContext;
import pe.ayni.skills.domain.model.CatalogItem;
import pe.ayni.skills.domain.model.LearningInterest;
import pe.ayni.skills.domain.model.OfferedSkill;
import pe.ayni.skills.domain.model.OfferedSkillStatus;
import pe.ayni.skills.domain.model.SkillsStateConflict;
import pe.ayni.skills.infrastructure.CatalogItemRepository;
import pe.ayni.skills.infrastructure.LearningInterestRepository;
import pe.ayni.skills.infrastructure.OfferedSkillRepository;

/**
 * US40: the last step of the onboarding. The student says what they need help with and what they
 * can teach, and their account is set up.
 *
 * <p>Everything happens in one transaction: identity moves the step to COMPLETED, the needs are
 * registered and the courses the student can teach go through the academic record path. When any of
 * it is refused nothing stays, so a student who is turned away resumes from the same step.
 *
 * <p>Identity is asked first, before anything is declared, and it refuses unless the profile and
 * the academic record are done. A student already COMPLETED is let through: identity changes nothing
 * for them, and this step sent twice, a retry or a double click, answers the same as the first time.
 */
@Service
public class CompleteOnboardingUseCase {

  private final IdentityApi identity;
  private final DeclareInterestsUseCase declareInterests;
  private final LearningInterestRepository learningInterests;
  private final CatalogItemRepository catalogItems;
  private final OfferedSkillRepository offeredSkills;

  CompleteOnboardingUseCase(
      IdentityApi identity,
      DeclareInterestsUseCase declareInterests,
      LearningInterestRepository learningInterests,
      CatalogItemRepository catalogItems,
      OfferedSkillRepository offeredSkills) {
    this.identity = identity;
    this.declareInterests = declareInterests;
    this.learningInterests = learningInterests;
    this.catalogItems = catalogItems;
    this.offeredSkills = offeredSkills;
  }

  /**
   * What the student ended up with.
   *
   * @param learning the items they need help with, in the order they were sent
   * @param teaching their enabled skills among the items they said they can teach
   */
  public record Onboarded(List<CatalogItem> learning, List<OfferedSkill> teaching) {}

  /**
   * @throws NoSuchElementException when the student or an item does not exist in this university
   * @throws SkillsStateConflict when the student has not done the profile and the academic record,
   *     or the person is not a student
   */
  @Transactional
  public Onboarded execute(UUID studentId, List<UUID> learning, List<UUID> teaching) {
    Objects.requireNonNull(studentId, "studentId must not be null");
    Objects.requireNonNull(learning, "learning must not be null");
    Objects.requireNonNull(teaching, "teaching must not be null");

    String tenantId = TenantContext.require();

    // Taken before the step is read: a second submission at the same time waits here and then finds
    // what the first one declared, instead of declaring it again and failing on the unique keys.
    learningInterests.lockInterestsOf(tenantId, studentId);

    if (identity.requireUser(studentId).role() != UserRole.STUDENT) {
      throw new SkillsStateConflict("only a student has an onboarding");
    }
    try {
      identity.completeOnboarding(studentId);
    } catch (IllegalStateException refused) {
      // IdentityApi's refusal of the step: the profile or the academic record is missing.
      throw new SkillsStateConflict(refused.getMessage());
    }

    List<LearningInterest> needs = declareInterests.declareLearningInterests(studentId, learning);
    declareInterests.declareTeachingInterests(studentId, teaching);

    return new Onboarded(itemsOf(needs, tenantId), enabledAmong(teaching, studentId, tenantId));
  }

  private List<CatalogItem> itemsOf(List<LearningInterest> needs, String tenantId) {
    if (needs.isEmpty()) {
      return List.of();
    }
    Map<UUID, CatalogItem> items =
        catalogItems
            .findAllByIdInAndTenantVisibility(
                needs.stream().map(LearningInterest::getCatalogItemId).toList(), tenantId)
            .stream()
            .collect(Collectors.toMap(CatalogItem::getId, Function.identity()));
    return needs.stream().map(need -> items.get(need.getCatalogItemId())).toList();
  }

  /**
   * Read after declaring rather than taken from what this request enabled, so a step sent again
   * answers the skills the first one enabled too.
   */
  private List<OfferedSkill> enabledAmong(List<UUID> teaching, UUID studentId, String tenantId) {
    Set<UUID> asked = new LinkedHashSet<>(teaching);
    return offeredSkills
        .findByTenantIdAndTutorIdAndStatus(tenantId, studentId, OfferedSkillStatus.ENABLED)
        .stream()
        .filter(skill -> asked.contains(skill.getCatalogItemId()))
        .toList();
  }
}
