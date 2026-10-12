package pe.ayni.skills.application;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.BinaryOperator;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.ayni.identity.ApprovedCourseView;
import pe.ayni.identity.IdentityApi;
import pe.ayni.shared.tenancy.TenantContext;
import pe.ayni.skills.domain.model.CatalogItem;
import pe.ayni.skills.domain.model.CatalogItemStatus;
import pe.ayni.skills.domain.model.OfferedSkillStatus;
import pe.ayni.skills.infrastructure.CatalogItemRepository;
import pe.ayni.skills.infrastructure.OfferedSkillRepository;

/**
 * US13, scenario 2: courses the tutor's academic record already clears, so they can be offered
 * without the tutor having to search for them.
 *
 * <p>A course is suggested only when offering it would succeed: it is an active course of the
 * university, its grade reaches the university's threshold, and the tutor does not hold it. One the
 * tutor withdrew is suggested again, since {@link OfferApprovedCourseUseCase} offers it again.
 *
 * <p>The answer costs the same whatever the length of the academic record: one read of the
 * matching courses and one of the tutor's offers, instead of two queries per approved course.
 */
@Service
public class SuggestedCoursesQuery {

  private final CatalogItemRepository catalogItems;
  private final OfferedSkillRepository offeredSkills;
  private final IdentityApi identity;
  private final TeachingThreshold threshold;

  SuggestedCoursesQuery(
      CatalogItemRepository catalogItems,
      OfferedSkillRepository offeredSkills,
      IdentityApi identity,
      TeachingThreshold teachingThreshold) {
    this.catalogItems = catalogItems;
    this.offeredSkills = offeredSkills;
    this.identity = identity;
    this.threshold = teachingThreshold;
  }

  /** A catalog item the tutor could offer, with the grade that would enable it. */
  public record SuggestedCourse(CatalogItem item, BigDecimal grade) {}

  @Transactional(readOnly = true)
  public List<SuggestedCourse> forTutor(UUID tutorId) {
    String tenantId = TenantContext.require();
    BigDecimal minimumGrade = threshold.of(tenantId);

    // A course taken twice appears once per term; the best grade is the one that counts.
    Map<String, BigDecimal> clearingGrades =
        identity.approvedCourses(tutorId).stream()
            .filter(course -> course.grade().compareTo(minimumGrade) >= 0)
            .collect(
                Collectors.toMap(
                    ApprovedCourseView::courseCode,
                    ApprovedCourseView::grade,
                    BinaryOperator.maxBy(Comparator.naturalOrder())));
    if (clearingGrades.isEmpty()) {
      return List.of();
    }

    Set<UUID> alreadyOffered =
        Set.copyOf(
            offeredSkills.findCatalogItemIdsNotIn(tenantId, tutorId, OfferedSkillStatus.WITHDRAWN));

    return catalogItems
        .findByTenantIdAndCourseCodeInAndStatus(
            tenantId, clearingGrades.keySet(), CatalogItemStatus.ACTIVE)
        .stream()
        .filter(item -> !alreadyOffered.contains(item.getId()))
        .sorted(Comparator.comparing(CatalogItem::getName))
        .map(item -> new SuggestedCourse(item, clearingGrades.get(item.getCourseCode())))
        .toList();
  }
}
