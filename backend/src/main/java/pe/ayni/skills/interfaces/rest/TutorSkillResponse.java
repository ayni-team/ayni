package pe.ayni.skills.interfaces.rest;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import pe.ayni.skills.application.TutorSkillsQuery;
import pe.ayni.skills.domain.model.ValidationRequest;

/**
 * One skill of the tutor, as US18 asks for it: its status, the path it was accredited through and
 * the date its status last changed. For a global tool, also where its evidence stands (US16).
 *
 * @param accreditationPath {@code null} while no path has accredited it
 * @param accreditedGrade the grade that enabled it, only for a course accredited by the record
 * @param statusChangedAt when the skill last changed status, which is its creation for a skill that
 *     never has
 * @param review the last submission of evidence, or {@code null} for a skill that needed none
 */
@Schema(name = "TutorSkill", description = "A skill the tutor offers or offered, with where it stands")
public record TutorSkillResponse(
    @Schema(example = "c0000000-0000-4000-8000-000000000001") UUID id,
    @Schema(example = "b0000000-0000-4000-8000-000000000101") UUID catalogItemId,
    @Schema(example = "Software Architecture Fundamentals") String name,
    @Schema(example = "UNIVERSITY") String scope,
    @Schema(nullable = true, example = "1ASI0657") String courseCode,
    @Schema(example = "ENABLED") String status,
    @Schema(nullable = true, example = "ACADEMIC_RECORD") String accreditationPath,
    @Schema(nullable = true, example = "17.50") BigDecimal accreditedGrade,
    @Schema(example = "2026-10-04T15:30:00Z") Instant statusChangedAt,
    @Schema(nullable = true) Review review) {

  /**
   * Where the evidence of a global tool stands, as the student may read it.
   *
   * @param decidedAt {@code null} while it waits
   * @param decisionReason why it was refused, or the note of the reviewer; {@code null} if none
   */
  @Schema(name = "TutorSkillReview", description = "The last submission of evidence for the skill")
  public record Review(
      @Schema(example = "SUBMITTED") String status,
      @Schema(example = "2026-10-04T15:30:00Z") Instant submittedAt,
      @Schema(nullable = true) Instant decidedAt,
      @Schema(nullable = true, example = "The certificate is not legible") String decisionReason) {

    static Review of(ValidationRequest request) {
      if (request == null) {
        return null;
      }
      return new Review(
          request.getStatus().name(),
          request.getCreatedAt(),
          request.getReviewedAt(),
          request.getDecisionReason());
    }
  }

  static TutorSkillResponse of(TutorSkillsQuery.TutorSkill tutorSkill) {
    var skill = tutorSkill.skill();
    var item = tutorSkill.item();
    return new TutorSkillResponse(
        skill.getId(),
        item.getId(),
        item.getName(),
        item.getScope().name(),
        item.getCourseCode(),
        skill.getStatus().name(),
        skill.getAccreditationPath() == null ? null : skill.getAccreditationPath().name(),
        skill.getAccreditedGrade(),
        skill.getUpdatedAt(),
        Review.of(tutorSkill.latestReview()));
  }
}
