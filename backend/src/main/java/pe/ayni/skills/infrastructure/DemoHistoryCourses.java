package pe.ayni.skills.infrastructure;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import pe.ayni.shared.demo.DemoCourses;
import pe.ayni.shared.tenancy.TenantContext;
import pe.ayni.skills.domain.model.CatalogItem;
import pe.ayni.skills.domain.model.CatalogItemStatus;

/** Skills' part of the history of the {@code demo} profile: finding a course by its code. */
@Component
@Profile("demo")
class DemoHistoryCourses implements DemoCourses {

  private final CatalogItemRepository catalogItems;

  DemoHistoryCourses(CatalogItemRepository catalogItems) {
    this.catalogItems = catalogItems;
  }

  @Override
  public Optional<UUID> courseCoded(String courseCode) {
    return catalogItems
        .findByTenantIdAndCourseCodeInAndStatus(
            TenantContext.require(), List.of(courseCode), CatalogItemStatus.ACTIVE)
        .stream()
        .map(CatalogItem::getId)
        .findFirst();
  }
}
