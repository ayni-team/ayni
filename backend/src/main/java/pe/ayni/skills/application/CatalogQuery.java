package pe.ayni.skills.application;

import java.util.Locale;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.ayni.identity.ApprovedCourseView;
import pe.ayni.identity.IdentityApi;
import pe.ayni.shared.tenancy.TenantContext;
import pe.ayni.skills.domain.model.CatalogItem;
import pe.ayni.skills.domain.model.CatalogItemStatus;
import pe.ayni.skills.infrastructure.CatalogItemRepository;

/** What a student of the current tenant may browse: the global items plus their own university's. */
@Service
public class CatalogQuery {

  private final CatalogItemRepository catalogItems;
  private final IdentityApi identity;

  CatalogQuery(CatalogItemRepository catalogItems, IdentityApi identity) {
    this.catalogItems = catalogItems;
    this.identity = identity;
  }

  /**
   * One page of the active items visible to the current university, sorted by name.
   *
   * @param categoryId {@code null} for every category
   * @param text part of the name, ignoring case; {@code null} or blank for every name
   */
  @Transactional(readOnly = true)
  public Page<CatalogItem> visibleItems(UUID categoryId, String text, int page, int size) {
    return visibleItems(categoryId, text, page, size, null, false);
  }

  /**
   * The same page, leaving out the student's own approved courses when {@code excludeApproved} is
   * asked: a course the student's academic record already backs is not something to ask for help
   * with (US40, FE-03). Global tools have no course code and are never excluded.
   *
   * @param studentId the student to read the approved courses of; ignored when {@code
   *     excludeApproved} is {@code false}
   */
  @Transactional(readOnly = true)
  public Page<CatalogItem> visibleItems(
      UUID categoryId, String text, int page, int size, UUID studentId, boolean excludeApproved) {
    String tenantId = TenantContext.require();
    Set<String> excludedCourseCodes =
        excludeApproved
            ? identity.approvedCourses(studentId).stream()
                .map(ApprovedCourseView::courseCode)
                .collect(Collectors.toSet())
            : Set.of();
    return catalogItems.searchVisible(
        tenantId,
        CatalogItemStatus.ACTIVE,
        categoryId,
        namePattern(text),
        excludedCourseCodes,
        PageRequest.of(page, size, Sort.by("name", "id")));
  }

  /**
   * One item the current university can see, whatever its status: the screens of a past tutoring
   * must still name a course that was retired since, which the browsable listing no longer shows.
   *
   * @throws NoSuchElementException when the item does not exist or belongs to another university
   */
  @Transactional(readOnly = true)
  public CatalogItem visibleItem(UUID catalogItemId) {
    return catalogItems
        .findByIdAndTenantVisibility(catalogItemId, TenantContext.require())
        .orElseThrow(
            () -> new NoSuchElementException("catalog item %s not found".formatted(catalogItemId)));
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
