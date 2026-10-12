package pe.ayni.skills.application;

import java.math.BigDecimal;
import org.springframework.stereotype.Component;
import pe.ayni.identity.IdentityApi;
import pe.ayni.skills.infrastructure.AcademicSettingsRepository;

/**
 * The grade a student needs in a course to teach it, in force today.
 *
 * <p>It is what the coordinator set (US51), or, while they have not, what the university was
 * registered with. Whoever enables a course asks here, so the two never disagree.
 */
@Component
class TeachingThreshold {

  private final AcademicSettingsRepository settings;
  private final IdentityApi identity;

  TeachingThreshold(AcademicSettingsRepository settings, IdentityApi identity) {
    this.settings = settings;
    this.identity = identity;
  }

  BigDecimal of(String tenantId) {
    return settings
        .findById(tenantId)
        .map(setting -> setting.getMinimumTeachingGrade())
        .orElseGet(() -> identity.requireTenant(tenantId).minimumTeachingGrade());
  }
}
