package pe.ayni.audit.infrastructure;

import jakarta.persistence.LockModeType;
import java.time.Instant;
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
import pe.ayni.audit.domain.model.Anomaly;
import pe.ayni.audit.domain.model.AnomalyStatus;

/** The anomalies of the platform. Every query starts from the university. */
public interface AnomalyRepository extends JpaRepository<Anomaly, UUID> {

  Optional<Anomaly> findByTenantIdAndId(String tenantId, UUID id);

  /** Reads an anomaly and locks it, so two decisions at once are taken one after the other. */
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select a from Anomaly a where a.tenantId = :tenantId and a.id = :id")
  Optional<Anomaly> lockByTenantIdAndId(@Param("tenantId") String tenantId, @Param("id") UUID id);

  Page<Anomaly> findByTenantId(String tenantId, Pageable pageable);

  Page<Anomaly> findByTenantIdAndStatus(String tenantId, AnomalyStatus status, Pageable pageable);

  List<Anomaly> findByTenantIdAndIdInAndStatus(
      String tenantId, Collection<UUID> ids, AnomalyStatus status);

  /**
   * Whether the pair was already pointed out since the given moment, whatever was decided about it.
   */
  @Query(
      value =
          """
          select count(*) > 0 from audit.anomalies
          where tenant_id = :tenantId and kind = 'REPEATED_PAIR' and subject_id = :tutorId
            and detail ->> 'studentId' = :studentId and detected_at > :since
          """,
      nativeQuery = true)
  boolean pairPointedOutSince(
      @Param("tenantId") String tenantId,
      @Param("tutorId") UUID tutorId,
      @Param("studentId") String studentId,
      @Param("since") Instant since);

  /** Serialises the detection for one pair, until the transaction ends. */
  @Query(
      value = "select cast(pg_advisory_xact_lock(7047001, hashtext(:key)) as text)",
      nativeQuery = true)
  String lockPair(@Param("key") String key);
}
