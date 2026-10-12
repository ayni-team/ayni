package pe.ayni.analytics.infrastructure;

import java.util.UUID;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;
import pe.ayni.analytics.domain.model.CountedEvent;
import pe.ayni.analytics.domain.model.CountedEventId;

/** What the projection already counted. */
public interface CountedEventRepository extends Repository<CountedEvent, CountedEventId> {

  /**
   * Marks a fact as counted.
   *
   * @return 1 when it was not counted yet, 0 when it was. The primary key decides, so two
   *     deliveries at the same moment cannot both get 1.
   */
  @Modifying(flushAutomatically = true, clearAutomatically = true)
  @Query(
      value =
          """
          insert into analytics.counted_events (tenant_id, kind, source_id)
          values (:tenantId, :kind, :sourceId)
          on conflict do nothing
          """,
      nativeQuery = true)
  int markCounted(
      @Param("tenantId") String tenantId,
      @Param("kind") String kind,
      @Param("sourceId") UUID sourceId);
}
