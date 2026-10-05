package pe.ayni.skills.application;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.ayni.shared.tenancy.TenantContext;
import pe.ayni.skills.application.MinimumGradeQuery.MinimumGrade;
import pe.ayni.skills.domain.model.AcademicSettings;
import pe.ayni.skills.domain.model.SkillsRuleViolation;
import pe.ayni.skills.infrastructure.AcademicSettingsRepository;

/**
 * US51, scenarios 2 and 3: a coordinator defines the grade a student needs in a course to teach it.
 *
 * <p>The grade is what {@link OfferApprovedCourseUseCase} checks when a student offers a course, so
 * the offer is enabled on its own only if the grade reaches it. Changing it applies to the offers made
 * from then on. The offers already granted are not looked at again: each one kept the grade it was
 * enabled with, and nothing here touches them.
 *
 * <p>A student who withdrew a course and offers it again is checked against the grade in force that
 * day, as for any new enablement.
 */
@Service
public class SetMinimumGradeUseCase {

  private final CoordinatorGuard coordinators;
  private final AcademicSettingsRepository settings;
  private final Clock clock;

  SetMinimumGradeUseCase(CoordinatorGuard coordinators, AcademicSettingsRepository settings, Clock clock) {
    this.coordinators = coordinators;
    this.settings = settings;
    this.clock = clock;
  }

  /**
   * @param minimumGrade on the scale of 0 to 20, with at most two decimals
   * @throws pe.ayni.skills.domain.model.NotACoordinator when the person is not a coordinator
   * @throws SkillsRuleViolation when the grade is outside the scale or has more than two decimals
   */
  @Transactional
  public MinimumGrade execute(UUID coordinatorId, BigDecimal minimumGrade) {
    Objects.requireNonNull(coordinatorId, "coordinatorId must not be null");
    Objects.requireNonNull(minimumGrade, "minimumGrade must not be null");
    if (minimumGrade.signum() < 0 || minimumGrade.compareTo(AcademicSettings.HIGHEST_GRADE) > 0) {
      throw new SkillsRuleViolation("the minimum grade must be between 0 and 20");
    }
    if (minimumGrade.stripTrailingZeros().scale() > 2) {
      throw new SkillsRuleViolation("the minimum grade takes at most two decimals");
    }

    coordinators.require(coordinatorId);
    String tenantId = TenantContext.require();

    BigDecimal grade = minimumGrade.setScale(2, RoundingMode.UNNECESSARY);
    Instant now = clock.instant();
    settings.saveMinimumGrade(tenantId, grade, coordinatorId, now);
    return new MinimumGrade(grade, now);
  }
}
