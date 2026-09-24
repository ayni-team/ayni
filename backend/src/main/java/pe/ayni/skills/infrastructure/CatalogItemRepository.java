package pe.ayni.skills.infrastructure;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import pe.ayni.skills.domain.model.CatalogItem;
import pe.ayni.skills.domain.model.CatalogItemStatus;

/**
 * Every method takes the university: a global item has no {@code tenant_id} to filter by, so
 * "visible to this tenant" always means global-or-mine, never a plain equality.
 */
public interface CatalogItemRepository extends JpaRepository<CatalogItem, UUID> {

  @Query(
      """
      select item from CatalogItem item
      where item.status = :status
        and (item.scope = pe.ayni.skills.CatalogScope.GLOBAL or item.tenantId = :tenantId)
        and (cast(:categoryId as uuid) is null or item.categoryId = :categoryId)
        and (cast(:namePattern as string) is null or lower(item.name) like :namePattern)
      """)
  Page<CatalogItem> searchVisible(
      @Param("tenantId") String tenantId,
      @Param("status") CatalogItemStatus status,
      @Param("categoryId") UUID categoryId,
      @Param("namePattern") String namePattern,
      Pageable pageable);

  @Query(
      """
      select item from CatalogItem item
      where item.id = :id
        and (item.scope = pe.ayni.skills.CatalogScope.GLOBAL or item.tenantId = :tenantId)
      """)
  Optional<CatalogItem> findByIdAndTenantVisibility(
      @Param("id") UUID id, @Param("tenantId") String tenantId);

  /** The university's courses in the given status among the codes the academic system reports. */
  List<CatalogItem> findByTenantIdAndCourseCodeInAndStatus(
      String tenantId, Collection<String> courseCodes, CatalogItemStatus status);
}
