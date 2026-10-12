package pe.ayni.audit.infrastructure;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;
import pe.ayni.audit.domain.model.ActivityEntry;

/**
 * What the activity log says about completed sessions, read to find pairs that meet too often.
 *
 * <p>It reads what audit already recorded and asks no other module. A session is counted once even
 * if its completion was recorded twice, because an event delivered twice is two entries.
 */
public interface CompletedSessionsRepository extends Repository<ActivityEntry, UUID> {

  /** A tutor and a student, and how many sessions they completed together. */
  interface PairCount {
    String getTutorId();

    String getStudentId();

    long getSessions();
  }

  /** The pairs of one university with more than {@code threshold} completed sessions in the period. */
  @Query(
      value =
          """
          select metadata ->> 'tutorId' as "tutorId",
                 metadata ->> 'studentId' as "studentId",
                 count(distinct resource_id) as "sessions"
          from audit.activity_log
          where tenant_id = :tenantId and action = 'SESSION_COMPLETED'
            and resource_id is not null
            and metadata ->> 'tutorId' is not null and metadata ->> 'studentId' is not null
            and occurred_at >= :from and occurred_at <= :to
          group by metadata ->> 'tutorId', metadata ->> 'studentId'
          having count(distinct resource_id) > :threshold
          order by count(distinct resource_id) desc, metadata ->> 'tutorId', metadata ->> 'studentId'
          """,
      nativeQuery = true)
  List<PairCount> pairsAbove(
      @Param("tenantId") String tenantId,
      @Param("from") Instant from,
      @Param("to") Instant to,
      @Param("threshold") long threshold);

  /** The sessions a pair completed in the period, each once. */
  @Query(
      value =
          """
          select distinct resource_id
          from audit.activity_log
          where tenant_id = :tenantId and action = 'SESSION_COMPLETED'
            and resource_id is not null
            and metadata ->> 'tutorId' = :tutorId and metadata ->> 'studentId' = :studentId
            and occurred_at >= :from and occurred_at <= :to
          order by resource_id
          """,
      nativeQuery = true)
  List<UUID> sessionsOf(
      @Param("tenantId") String tenantId,
      @Param("tutorId") String tutorId,
      @Param("studentId") String studentId,
      @Param("from") Instant from,
      @Param("to") Instant to);
}
