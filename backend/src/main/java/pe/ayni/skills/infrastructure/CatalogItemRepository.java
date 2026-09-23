package pe.ayni.skills.infrastructure;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
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

  /** Items of the given status a student of {@code tenantId} may see: global ones plus their own. */
  @Query(
      """
      select item from CatalogItem item
      where item.status = :status
        and (item.scope = pe.ayni.skills.CatalogScope.GLOBAL or item.tenantId = :tenantId)
      """)
  List<CatalogItem> findByTenantVisibilityAndStatus(
      @Param("tenantId") String tenantId, @Param("status") CatalogItemStatus status);

  @Query(
      """
      select item from CatalogItem item
      where item.id = :id
        and (item.scope = pe.ayni.skills.CatalogScope.GLOBAL or item.tenantId = :tenantId)
      """)
  Optional<CatalogItem> findByIdAndTenantVisibility(
      @Param("id") UUID id, @Param("tenantId") String tenantId);

  /** The university course that matches a code the academic system reports as approved. */
  Optional<CatalogItem> findByTenantIdAndCourseCode(String tenantId, String courseCode);
}
