package pe.ayni.skills.application;

import java.util.Locale;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
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

  /**
   * One page of the active items visible to the current university, sorted by name.
   *
   * @param categoryId {@code null} for every category
   * @param text part of the name, ignoring case; {@code null} or blank for every name
   */
  @Transactional(readOnly = true)
  public Page<CatalogItem> visibleItems(UUID categoryId, String text, int page, int size) {
    String tenantId = TenantContext.require();
    return catalogItems.searchVisible(
        tenantId,
        CatalogItemStatus.ACTIVE,
        categoryId,
        namePattern(text),
        PageRequest.of(page, size, Sort.by("name", "id")));
  }

  /** A {@code LIKE} pattern that treats the student's text literally, wildcards included. */
  private static String namePattern(String text) {
    if (text == null || text.isBlank()) {
      return null;
    }
    String literal =
        text.strip()
            .toLowerCase(Locale.ROOT)
            .replace("\\", "\\\\")
            .replace("%", "\\%")
            .replace("_", "\\_");
    return "%" + literal + "%";
  }
}
