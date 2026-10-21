package pe.ayni.skills.application;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.ayni.shared.tenancy.TenantContext;
import pe.ayni.skills.domain.model.CatalogItem;
import pe.ayni.skills.domain.model.LearningInterest;
import pe.ayni.skills.infrastructure.CatalogItemRepository;
import pe.ayni.skills.infrastructure.LearningInterestRepository;

/**
 * US41: what a student says they need help with, as catalogue items sorted by name.
 *
 * <p>The whole set, not a page: it is what the student edits and sends back whole, and a request
 * never holds more than fifty of them.
 */
@Service
public class LearningInterestsQuery {

  private final LearningInterestRepository learningInterests;
  private final CatalogItemRepository catalogItems;

  LearningInterestsQuery(
      LearningInterestRepository learningInterests, CatalogItemRepository catalogItems) {
    this.learningInterests = learningInterests;
    this.catalogItems = catalogItems;
  }

  @Transactional(readOnly = true)
  public List<CatalogItem> of(UUID studentId) {
    String tenantId = TenantContext.require();

    List<UUID> declared =
        learningInterests.findByTenantIdAndStudentId(tenantId, studentId).stream()
            .map(LearningInterest::getCatalogItemId)
            .toList();
    if (declared.isEmpty()) {
      return List.of();
    }
    return catalogItems.findAllByIdInAndTenantVisibility(declared, tenantId).stream()
        .sorted(Comparator.comparing(CatalogItem::getName, String.CASE_INSENSITIVE_ORDER))
        .toList();
  }
}
