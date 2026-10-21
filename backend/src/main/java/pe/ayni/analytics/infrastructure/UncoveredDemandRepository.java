package pe.ayni.analytics.infrastructure;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;
import pe.ayni.analytics.domain.model.UncoveredDemand;
import pe.ayni.analytics.domain.model.UncoveredDemandId;

/** The rows of uncovered demand. Every query filters by the university. */
public interface UncoveredDemandRepository extends Repository<UncoveredDemand, UncoveredDemandId> {

  /** A course and what it added up to over a period. */
  interface CourseTotal {
    UUID getCatalogItemId();

    long getSearches();
  }

  /**
   * Counts one more search of a course on a day, creating the row when it is the first. It is one
   * statement, so two events arriving at once both count.
   */
  @Modifying(flushAutomatically = true, clearAutomatically = true)
  @Query(
      value =
          """
          insert into analytics.uncovered_demand (tenant_id, catalog_item_id, day, searches)
          values (:tenantId, :catalogItemId, :day, 1)
          on conflict (tenant_id, catalog_item_id, day) do update set
              searches = uncovered_demand.searches + 1
          """,
      nativeQuery = true)
  void countOneMore(
      @Param("tenantId") String tenantId,
      @Param("catalogItemId") UUID catalogItemId,
      @Param("day") LocalDate day);

  /**
   * The most searched courses of the period, both ends included, the most searched first. Courses
   * with the same count come in the order of their identifier, so the answer is always the same.
   */
  @Query(
      value =
          """
          select catalog_item_id as "catalogItemId", sum(searches) as "searches"
          from analytics.uncovered_demand
          where tenant_id = :tenantId and day >= :from and day <= :to
          group by catalog_item_id
          order by sum(searches) desc, catalog_item_id
          limit :limit
          """,
      nativeQuery = true)
  List<CourseTotal> mostSearched(
      @Param("tenantId") String tenantId,
      @Param("from") LocalDate from,
      @Param("to") LocalDate to,
      @Param("limit") int limit);
}
