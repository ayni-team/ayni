package pe.ayni.skills.interfaces.rest;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import pe.ayni.skills.domain.model.OfferedSkill;

/** What a tutor is now enabled to teach, right after the offer went through. */
@Schema(name = "OfferedSkill", description = "A skill the tutor is enabled to teach")
public record OfferedSkillResponse(
    @Schema(example = "c0000000-0000-4000-8000-000000000001") UUID id,
    @Schema(example = "b0000000-0000-4000-8000-000000000101") UUID catalogItemId,
    @Schema(example = "ENABLED") String status,
    @Schema(example = "ACADEMIC_RECORD") String accreditationPath,
    @Schema(example = "17.50") BigDecimal accreditedGrade,
    @Schema(example = "2026-09-22T19:08:39Z") Instant enabledAt) {

  static OfferedSkillResponse of(OfferedSkill skill) {
    return new OfferedSkillResponse(
        skill.getId(),
        skill.getCatalogItemId(),
        skill.getStatus().name(),
        skill.getAccreditationPath() == null ? null : skill.getAccreditationPath().name(),
        skill.getAccreditedGrade(),
        skill.getEnabledAt());
  }
}
