package pe.ayni.analytics.infrastructure;

import java.time.LocalDate;
import java.util.List;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;
import pe.ayni.analytics.domain.model.DailyIndicator;
import pe.ayni.analytics.domain.model.DailyIndicatorId;

/** The daily rows of the projection. Every query filters by the university. */
public interface DailyIndicatorRepository extends Repository<DailyIndicator, DailyIndicatorId> {

  /** The rows of the period that exist; a day with no activity has none. */
  List<DailyIndicator> findByTenantIdAndDayBetweenOrderByDay(
      String tenantId, LocalDate from, LocalDate to);

  /**
   * Adds to the counters of a day, creating it when it is the first thing that happened. It is one
   * statement, so two events arriving at once both count.
   */
  @Modifying(flushAutomatically = true, clearAutomatically = true)
  @Query(
      value =
          """
          insert into analytics.daily_indicators
              (tenant_id, day, sessions_completed, hours_exchanged, active_students,
               new_students, requests_approved)
          values (:tenantId, :day, :sessions, :hours, :active, :newStudents, :recognitions)
          on conflict (tenant_id, day) do update set
              sessions_completed = daily_indicators.sessions_completed + excluded.sessions_completed,
              hours_exchanged    = daily_indicators.hours_exchanged + excluded.hours_exchanged,
              active_students    = daily_indicators.active_students + excluded.active_students,
              new_students       = daily_indicators.new_students + excluded.new_students,
              requests_approved  = daily_indicators.requests_approved + excluded.requests_approved
          """,
      nativeQuery = true)
  void add(
      @Param("tenantId") String tenantId,
      @Param("day") LocalDate day,
      @Param("sessions") int sessions,
      @Param("hours") int hours,
      @Param("active") int active,
      @Param("newStudents") int newStudents,
      @Param("recognitions") int recognitions);
}
