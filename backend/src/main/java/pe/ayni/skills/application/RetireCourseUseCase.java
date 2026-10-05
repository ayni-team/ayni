package pe.ayni.skills.application;

import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.ayni.shared.tenancy.TenantContext;
import pe.ayni.skills.CatalogScope;
import pe.ayni.skills.application.RetireCatalogItemUseCase.Retirement;
import pe.ayni.skills.domain.model.CatalogItem;
import pe.ayni.skills.infrastructure.CatalogItemRepository;

/**
 * US51, scenario 4: a coordinator takes a course out of the curriculum of their university.
 *
 * <p>The course can no longer be offered and the search stops finding it. The tutors who offered it
 * have the offer withdrawn, and the sessions already booked stand. That is what retiring any
 * catalogue item does ({@link RetireCatalogItemUseCase}, US44), including the confirmation: the
 * coordinator sends back the number of tutors affected, and if it changed since they read it, nothing
 * is retired.
 *
 * <p>This is the door for the courses of the university alone. A global tool belongs to every
 * university and is cleaned up by a moderator of the catalogue, not removed from one curriculum, so
 * here it is not found.
 */
@Service
public class RetireCourseUseCase {

  private final CoordinatorGuard coordinators;
  private final CatalogItemRepository catalogItems;
  private final RetireCatalogItemUseCase retireItem;

  RetireCourseUseCase(
      CoordinatorGuard coordinators,
      CatalogItemRepository catalogItems,
      RetireCatalogItemUseCase retireItem) {
    this.coordinators = coordinators;
    this.catalogItems = catalogItems;
    this.retireItem = retireItem;
  }

  /**
   * @param confirmedTutors the number of tutors the coordinator saw as affected
   * @throws pe.ayni.skills.domain.model.NotACoordinator when the person is not a coordinator
   * @throws NoSuchElementException when it is not a course of this university
   * @throws pe.ayni.skills.domain.model.SkillsStateConflict when the course is already retired, or
   *     the number of tutors is no longer the one confirmed
   * @throws pe.ayni.skills.domain.model.SkillsRuleViolation when the confirmation is negative
   */
  @Transactional
  public Retirement execute(UUID coordinatorId, UUID courseId, long confirmedTutors) {
    Objects.requireNonNull(coordinatorId, "coordinatorId must not be null");
    Objects.requireNonNull(courseId, "courseId must not be null");

    coordinators.require(coordinatorId);
    String tenantId = TenantContext.require();

    CatalogItem item =
        catalogItems
            .findByIdAndTenantVisibility(courseId, tenantId)
            .filter(found -> found.getScope() == CatalogScope.UNIVERSITY)
            .orElseThrow(() -> new NoSuchElementException("course %s not found".formatted(courseId)));

    return retireItem.execute(coordinatorId, item.getId(), confirmedTutors);
  }
}
