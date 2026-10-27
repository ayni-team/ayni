package pe.ayni.analytics.infrastructure;

import java.time.LocalDate;
import java.util.UUID;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;
import pe.ayni.analytics.domain.model.ActiveStudentDay;
import pe.ayni.analytics.domain.model.ActiveStudentDayId;

/** Which students took part on which day. Nothing here leaves the module. */
public interface ActiveStudentDayRepository extends Repository<ActiveStudentDay, ActiveStudentDayId> {

  /**
   * Marks a student as active on a day.
   *
   * @return 1 when they were not yet, 0 when they already were
   */
  @Modifying(flushAutomatically = true, clearAutomatically = true)
  @Query(
      value =
          """
          insert into analytics.active_student_days (tenant_id, day, student_id)
          values (:tenantId, :day, :studentId)
          on conflict do nothing
          """,
      nativeQuery = true)
  int markActive(
      @Param("tenantId") String tenantId,
      @Param("day") LocalDate day,
      @Param("studentId") UUID studentId);

  /** Distinct people who took part in the period, both ends included. */
  @Query(
      value =
          """
          select count(distinct student_id)
          from analytics.active_student_days
          where tenant_id = :tenantId and day >= :from and day <= :to
          """,
      nativeQuery = true)
  long countDistinctBetween(
      @Param("tenantId") String tenantId, @Param("from") LocalDate from, @Param("to") LocalDate to);
}
