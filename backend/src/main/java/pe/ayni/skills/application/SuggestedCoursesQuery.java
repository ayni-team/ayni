package pe.ayni.skills.application;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.ayni.identity.ApprovedCourseView;
import pe.ayni.identity.IdentityApi;
import pe.ayni.shared.tenancy.TenantContext;
import pe.ayni.skills.domain.model.CatalogItem;
import pe.ayni.skills.infrastructure.CatalogItemRepository;
import pe.ayni.skills.infrastructure.OfferedSkillRepository;

/**
 * US13, scenario 2: courses the tutor's academic record already clears, so they can be offered
 * without the tutor having to search for them.
 *
 * <p>A course the tutor never approved has no catalog counterpart to match, and one already offered
 * (enabled, pending or even withdrawn) is left out: {@link OfferApprovedCourseUseCase} refuses a
 * second offer of the same item, so suggesting it again would only lead to a refusal.
 *
 * <p>{@code identity} is injected {@code @Lazy}, for the same reason as in {@link
 * OfferApprovedCourseUseCase}: identity has no implementation yet, and a plain injection would stop
 * every other module's Spring context from starting.
 */
@Service
public class SuggestedCoursesQuery {

  private final CatalogItemRepository catalogItems;
  private final OfferedSkillRepository offeredSkills;
  private final IdentityApi identity;

  SuggestedCoursesQuery(
      CatalogItemRepository catalogItems,
      OfferedSkillRepository offeredSkills,
      @Lazy IdentityApi identity) {
    this.catalogItems = catalogItems;
    this.offeredSkills = offeredSkills;
    this.identity = identity;
  }

  /** A catalog item the tutor could offer, with the grade that would enable it. */
  public record SuggestedCourse(CatalogItem item, BigDecimal grade) {}

  @Transactional(readOnly = true)
  public List<SuggestedCourse> forTutor(UUID tutorId) {
    String tenantId = TenantContext.require();

    return identity.approvedCourses(tutorId).stream()
        .map(course -> match(tenantId, tutorId, course))
        .flatMap(Optional::stream)
        .toList();
  }

  private Optional<SuggestedCourse> match(String tenantId, UUID tutorId, ApprovedCourseView course) {
    return catalogItems
        .findByTenantIdAndCourseCode(tenantId, course.courseCode())
        .filter(
            item ->
                offeredSkills
                    .findByTenantIdAndTutorIdAndCatalogItemId(tenantId, tutorId, item.getId())
                    .isEmpty())
        .map(item -> new SuggestedCourse(item, course.grade()));
  }
}
