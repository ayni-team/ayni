package pe.ayni.skills.infrastructure;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;
import pe.ayni.skills.domain.model.AcademicSettings;

public interface AcademicSettingsRepository extends JpaRepository<AcademicSettings, String> {

  /**
   * Sets the grade of a university, creating its row the first time.
   *
   * <p>One statement, so two coordinators saving at once never see the key taken: the later one
   * simply wins.
   */
  @Transactional
  @Modifying(flushAutomatically = true, clearAutomatically = true)
  @Query(
      value =
          """
          insert into skills.academic_settings
            (tenant_id, minimum_teaching_grade, updated_by, updated_at)
          values (:tenantId, :grade, :updatedBy, :updatedAt)
          on conflict (tenant_id) do update
            set minimum_teaching_grade = excluded.minimum_teaching_grade,
                updated_by = excluded.updated_by,
                updated_at = excluded.updated_at
          """,
      nativeQuery = true)
  int saveMinimumGrade(
      @Param("tenantId") String tenantId,
      @Param("grade") BigDecimal grade,
      @Param("updatedBy") UUID updatedBy,
      @Param("updatedAt") Instant updatedAt);
}
