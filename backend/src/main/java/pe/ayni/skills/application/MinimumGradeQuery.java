package pe.ayni.skills.application;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.ayni.shared.tenancy.TenantContext;
import pe.ayni.skills.infrastructure.AcademicSettingsRepository;

/**
 * US51: the grade a coordinator sees as the one their university asks of a student to teach a course.
 */
@Service
public class MinimumGradeQuery {

  private final CoordinatorGuard coordinators;
  private final TeachingThreshold threshold;
  private final AcademicSettingsRepository settings;

  MinimumGradeQuery(
      CoordinatorGuard coordinators, TeachingThreshold threshold, AcademicSettingsRepository settings) {
    this.coordinators = coordinators;
    this.threshold = threshold;
    this.settings = settings;
  }

  /**
   * @param grade the minimum grade in force
   * @param updatedAt when a coordinator last set it, or {@code null} while it is the grade the
   *     university was registered with
   */
  public record MinimumGrade(BigDecimal grade, Instant updatedAt) {}

  /**
   * @throws pe.ayni.skills.domain.model.NotACoordinator when the person is not a coordinator
   */
  @Transactional(readOnly = true)
  public MinimumGrade current(UUID coordinatorId) {
    Objects.requireNonNull(coordinatorId, "coordinatorId must not be null");
    coordinators.require(coordinatorId);
    String tenantId = TenantContext.require();
    return new MinimumGrade(
        threshold.of(tenantId),
        settings.findById(tenantId).map(setting -> setting.getUpdatedAt()).orElse(null));
  }
}
