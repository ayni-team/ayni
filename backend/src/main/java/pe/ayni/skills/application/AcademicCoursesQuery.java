package pe.ayni.skills.application;

import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.ayni.shared.tenancy.TenantContext;
import pe.ayni.skills.CatalogScope;
import pe.ayni.skills.domain.model.CatalogItem;
import pe.ayni.skills.infrastructure.CatalogItemRepository;

/**
 * US51: the courses a university has loaded, which is what the coordinator checks after loading
 * them and before retiring one.
 *
 * <p>It lists the retired ones too, marked as such: a coordinator who loaded a curriculum must be
 * able to see that a course they expect is missing because it was retired, not because it was never
 * saved.
 */
@Service
public class AcademicCoursesQuery {

  private final CoordinatorGuard coordinators;
  private final CatalogItemRepository catalogItems;

  AcademicCoursesQuery(CoordinatorGuard coordinators, CatalogItemRepository catalogItems) {
    this.coordinators = coordinators;
    this.catalogItems = catalogItems;
  }

  /**
   * @return every course of the coordinator's university, by course code
   * @throws pe.ayni.skills.domain.model.NotACoordinator when the person is not a coordinator
   */
  @Transactional(readOnly = true)
  public List<CatalogItem> of(UUID coordinatorId) {
    Objects.requireNonNull(coordinatorId, "coordinatorId must not be null");
    coordinators.require(coordinatorId);
    String tenantId = TenantContext.require();
    return catalogItems.findByTenantIdAndScopeOrderByCourseCodeAsc(tenantId, CatalogScope.UNIVERSITY);
  }
}
