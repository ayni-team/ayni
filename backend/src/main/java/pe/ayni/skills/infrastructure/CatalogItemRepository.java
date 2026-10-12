package pe.ayni.skills.infrastructure;

import jakarta.persistence.LockModeType;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import pe.ayni.skills.CatalogScope;
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

  /**
   * Waits for its turn to add a global item, until the transaction ends.
   *
   * <p>The catalogue does not hold a global name twice, but nothing in the table says so: global
   * items have no university to scope a unique index by, and the tools of the seed data share names
   * on purpose in some tests. Two universities approving the same tool at once would each find the
   * name free and each create it. Whoever approves takes this lock first, then looks for the name,
   * so the second one finds the first one's item.
   *
   * <p>The lock is a number of this module's own, taken from the database, so it also holds across
   * several instances of the application.
   *
   * @return nothing useful: only the wait matters
   */
  @Query(value = "select cast(pg_advisory_xact_lock(7043001) as text)", nativeQuery = true)
  String lockGlobalCatalogue();

  /** Every item in the given status visible to the university, to compare a name against all. */
  @Query(
      """
      select item from CatalogItem item
      where item.status = :status
        and (item.scope = pe.ayni.skills.CatalogScope.GLOBAL or item.tenantId = :tenantId)
      """)
  List<CatalogItem> findVisible(
      @Param("tenantId") String tenantId, @Param("status") CatalogItemStatus status);

  @Query(
      """
      select item from CatalogItem item
      where item.id = :id
        and (item.scope = pe.ayni.skills.CatalogScope.GLOBAL or item.tenantId = :tenantId)
      """)
  Optional<CatalogItem> findByIdAndTenantVisibility(
      @Param("id") UUID id, @Param("tenantId") String tenantId);

  /**
   * An item visible to the university, locked for writing until the transaction ends. Whoever
   * retires or joins an item takes this lock, so nobody can start offering it in the meantime.
   */
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query(
      """
      select item from CatalogItem item
      where item.id = :id
        and (item.scope = pe.ayni.skills.CatalogScope.GLOBAL or item.tenantId = :tenantId)
      """)
  Optional<CatalogItem> lockByIdAndTenantVisibility(
      @Param("id") UUID id, @Param("tenantId") String tenantId);

  /**
   * An item visible to the university, locked for sharing until the transaction ends. Whoever
   * starts offering an item takes this one: any number of tutors can at once, but none while the
   * item is being retired, and a retirement waits for them. The tutor then finds the item as the
   * retirement left it, and cannot be left offering something that was just taken out.
   *
   * <p>Only for transactions that write: PostgreSQL does not lock rows in a read only one.
   */
  @Lock(LockModeType.PESSIMISTIC_READ)
  @Query(
      """
      select item from CatalogItem item
      where item.id = :id
        and (item.scope = pe.ayni.skills.CatalogScope.GLOBAL or item.tenantId = :tenantId)
      """)
  Optional<CatalogItem> lockByIdAndTenantVisibilityForShare(
      @Param("id") UUID id, @Param("tenantId") String tenantId);

  /** The university's courses in the given status among the codes the academic system reports. */
  List<CatalogItem> findByTenantIdAndCourseCodeInAndStatus(
      String tenantId, Collection<String> courseCodes, CatalogItemStatus status);

  /**
   * Waits for its turn to change the courses of one university, until the transaction ends.
   *
   * <p>A course code is unique per university, but finding which codes already exist and adding the
   * rest are two steps. Two coordinators loading the same curriculum at once would each find a code
   * free and each add it, and the database would refuse the second. Whoever loads takes this lock
   * first, so the second one finds what the first one added.
   *
   * <p>The lock is taken from the database, so it also holds across several instances of the
   * application. It is per university: others do not wait.
   *
   * @return nothing useful: only the wait matters
   */
  @Query(
      value = "select cast(pg_advisory_xact_lock(7043002, hashtext(:tenantId)) as text)",
      nativeQuery = true)
  String lockCoursesOf(@Param("tenantId") String tenantId);

  /** Whichever of these codes the university has, active or retired. */
  List<CatalogItem> findByTenantIdAndCourseCodeIn(String tenantId, Collection<String> courseCodes);

  /** Every course of the university, active or retired. */
  List<CatalogItem> findByTenantIdAndScopeOrderByCourseCodeAsc(String tenantId, CatalogScope scope);
}
