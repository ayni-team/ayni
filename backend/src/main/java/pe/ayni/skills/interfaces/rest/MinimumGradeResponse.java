package pe.ayni.skills.interfaces.rest;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.Instant;
import pe.ayni.skills.application.MinimumGradeQuery.MinimumGrade;

/** The grade in force, and when a coordinator set it. */
@Schema(name = "MinimumGrade", description = "The minimum grade needed to teach a course")
public record MinimumGradeResponse(
    @Schema(example = "14.00") BigDecimal minimumGrade,
    @Schema(
            example = "2026-10-05T09:00:00Z",
            nullable = true,
            description = "Absent while it is still the grade the university was registered with")
        Instant updatedAt) {

  static MinimumGradeResponse of(MinimumGrade grade) {
    return new MinimumGradeResponse(grade.grade(), grade.updatedAt());
  }
}
