package pe.ayni.skills.application;

import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.ayni.shared.tenancy.TenantContext;
import pe.ayni.skills.domain.model.CatalogItem;
import pe.ayni.skills.domain.model.CatalogItemStatus;
import pe.ayni.skills.infrastructure.CatalogItemRepository;

/**
 * The catalogue items that look like a name a student is about to propose.
 *
 * <p>It compares against every active item the student's university can see, courses included: a
 * tool proposed under the name of an existing course is the same skill with another spelling.
 */
@Service
public class SimilarItemsQuery {

  /** More than this is a wall of text, and the closest ones are what the student needs. */
  static final int MAX_RESULTS = 5;

  private final CatalogItemRepository catalogItems;

  SimilarItemsQuery(CatalogItemRepository catalogItems) {
    this.catalogItems = catalogItems;
  }

  /**
   * The items of the catalogue that look like the name, the closest first.
   *
   * @param name what the student would call the skill
   * @return at most {@value #MAX_RESULTS} items; empty when nothing looks like it
   */
  @Transactional(readOnly = true)
  public List<CatalogItem> similarTo(String name) {
    String tenantId = TenantContext.require();
    return catalogItems.findVisible(tenantId, CatalogItemStatus.ACTIVE).stream()
        .map(item -> new Scored(item, NameSimilarity.between(name, item.getName())))
        .filter(scored -> scored.score() >= NameSimilarity.THRESHOLD)
        .sorted(
            Comparator.comparingDouble(Scored::score)
                .reversed()
                .thenComparing(scored -> scored.item().getName()))
        .limit(MAX_RESULTS)
        .map(Scored::item)
        .toList();
  }

  private record Scored(CatalogItem item, double score) {}
}
