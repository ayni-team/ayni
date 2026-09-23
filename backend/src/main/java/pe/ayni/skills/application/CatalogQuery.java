package pe.ayni.skills.application;

import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.ayni.shared.tenancy.TenantContext;
import pe.ayni.skills.domain.model.CatalogItem;
import pe.ayni.skills.domain.model.CatalogItemStatus;
import pe.ayni.skills.infrastructure.CatalogItemRepository;

/** What a student of the current tenant may browse: the global items plus their own university's. */
@Service
public class CatalogQuery {

  private final CatalogItemRepository catalogItems;

  CatalogQuery(CatalogItemRepository catalogItems) {
    this.catalogItems = catalogItems;
  }

  @Transactional(readOnly = true)
  public List<CatalogItem> visibleItems() {
    String tenantId = TenantContext.require();
    return catalogItems.findByTenantVisibilityAndStatus(tenantId, CatalogItemStatus.ACTIVE);
  }
}
