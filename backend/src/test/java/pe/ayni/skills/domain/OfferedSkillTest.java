package pe.ayni.skills.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static pe.ayni.skills.domain.SkillsFixtures.NOW;
import static pe.ayni.skills.domain.SkillsFixtures.THRESHOLD;
import static pe.ayni.skills.domain.SkillsFixtures.TUTOR;
import static pe.ayni.skills.domain.SkillsFixtures.UPC;
import static pe.ayni.skills.domain.SkillsFixtures.enabledSkill;

import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import pe.ayni.skills.domain.model.AccreditationPath;
import pe.ayni.skills.domain.model.OfferedSkill;
import pe.ayni.skills.domain.model.OfferedSkillStatus;
import pe.ayni.skills.domain.model.SkillsRuleViolation;
import pe.ayni.skills.domain.model.SkillsStateConflict;

/** US13: a course a tutor already passed is enabled the moment the grade clears the threshold. */
class OfferedSkillTest {

  private final UUID catalogItemId = UUID.randomUUID();

  @Test
  @DisplayName("a grade that reaches the threshold enables the skill immediately")
  void gradeReachingTheThresholdEnablesTheSkillImmediately() {
    OfferedSkill skill = enabledSkill(catalogItemId, THRESHOLD);

    assertThat(skill.isEnabled()).isTrue();
    assertThat(skill.getStatus()).isEqualTo(OfferedSkillStatus.ENABLED);
    assertThat(skill.getAccreditationPath()).isEqualTo(AccreditationPath.ACADEMIC_RECORD);
    assertThat(skill.getAccreditedGrade()).isEqualByComparingTo(THRESHOLD);
    assertThat(skill.getEnabledAt()).isEqualTo(NOW);
  }

  @Test
  @DisplayName("a grade above the threshold also enables the skill")
  void gradeAboveTheThresholdAlsoEnablesTheSkill() {
    assertThat(enabledSkill(catalogItemId, new BigDecimal("18.00")).isEnabled()).isTrue();
  }

  @Test
  @DisplayName("a grade below the threshold is refused")
  void gradeBelowTheThresholdIsRefused() {
    assertThatThrownBy(
            () ->
                OfferedSkill.enableByAcademicRecord(
                    UUID.randomUUID(),
                    UPC,
                    TUTOR,
                    catalogItemId,
                    new BigDecimal("12.99"),
                    THRESHOLD,
                    NOW))
        .isInstanceOf(SkillsRuleViolation.class);
  }

  @Test
  @DisplayName("withdrawing an enabled skill takes it out of the offer")
  void withdrawingAnEnabledSkillTakesItOutOfTheOffer() {
    OfferedSkill skill = enabledSkill(catalogItemId, THRESHOLD);

    skill.withdraw(NOW.plusSeconds(60));

    assertThat(skill.isEnabled()).isFalse();
    assertThat(skill.getStatus()).isEqualTo(OfferedSkillStatus.WITHDRAWN);
  }

  @Test
  @DisplayName("a skill that is not enabled cannot be withdrawn")
  void aSkillThatIsNotEnabledCannotBeWithdrawn() {
    OfferedSkill skill = enabledSkill(catalogItemId, THRESHOLD);
    skill.withdraw(NOW.plusSeconds(60));

    assertThatThrownBy(() -> skill.withdraw(NOW.plusSeconds(120)))
        .isInstanceOf(SkillsStateConflict.class);
  }

  @Test
  @DisplayName("a withdrawn course is enabled again with the grade of today")
  void aWithdrawnCourseIsEnabledAgainWithTheGradeOfToday() {
    OfferedSkill skill = enabledSkill(catalogItemId, THRESHOLD);
    skill.withdraw(NOW.plusSeconds(60));

    skill.reEnableByAcademicRecord(new BigDecimal("17.00"), THRESHOLD, NOW.plusSeconds(120));

    assertThat(skill.isEnabled()).isTrue();
    assertThat(skill.getAccreditedGrade()).isEqualByComparingTo("17.00");
    assertThat(skill.getEnabledAt()).isEqualTo(NOW.plusSeconds(120));
    assertThat(skill.getUpdatedAt()).isEqualTo(NOW.plusSeconds(120));
    assertThat(skill.getAccreditationPath()).isEqualTo(AccreditationPath.ACADEMIC_RECORD);
  }

  @Test
  @DisplayName("a grade that no longer clears the threshold does not enable it again")
  void aGradeThatNoLongerClearsDoesNotEnableItAgain() {
    OfferedSkill skill = enabledSkill(catalogItemId, THRESHOLD);
    skill.withdraw(NOW.plusSeconds(60));

    assertThatThrownBy(
            () ->
                skill.reEnableByAcademicRecord(
                    new BigDecimal("12.00"), THRESHOLD, NOW.plusSeconds(120)))
        .isInstanceOf(SkillsRuleViolation.class);

    assertThat(skill.getStatus()).isEqualTo(OfferedSkillStatus.WITHDRAWN);
  }

  @Test
  @DisplayName("a global tool starts pending, with no path and no grade")
  void aGlobalToolStartsPending() {
    OfferedSkill skill = OfferedSkill.requestValidation(UUID.randomUUID(), UPC, TUTOR, catalogItemId, NOW);

    assertThat(skill.getStatus()).isEqualTo(OfferedSkillStatus.PENDING);
    assertThat(skill.isEnabled()).isFalse();
    assertThat(skill.getAccreditationPath()).isNull();
    assertThat(skill.getAccreditedGrade()).isNull();
    assertThat(skill.getEnabledAt()).isNull();
  }

  @Test
  @DisplayName("approving the evidence enables the skill by the reviewed evidence")
  void approvingTheEvidenceEnablesTheSkill() {
    OfferedSkill skill = OfferedSkill.requestValidation(UUID.randomUUID(), UPC, TUTOR, catalogItemId, NOW);

    skill.approveByReviewedEvidence(NOW.plusSeconds(60));

    assertThat(skill.isEnabled()).isTrue();
    assertThat(skill.getAccreditationPath()).isEqualTo(AccreditationPath.REVIEWED_EVIDENCE);
    assertThat(skill.getAccreditedGrade()).isNull();
    assertThat(skill.getEnabledAt()).isEqualTo(NOW.plusSeconds(60));
    assertThat(skill.getUpdatedAt()).isEqualTo(NOW.plusSeconds(60));
  }

  @Test
  @DisplayName("rejecting the evidence refuses the skill, and new evidence puts it back in the queue")
  void rejectedEvidenceCanBeSubmittedAgain() {
    OfferedSkill skill = OfferedSkill.requestValidation(UUID.randomUUID(), UPC, TUTOR, catalogItemId, NOW);

    skill.rejectEvidence(NOW.plusSeconds(60));
    assertThat(skill.getStatus()).isEqualTo(OfferedSkillStatus.REJECTED);
    assertThat(skill.getUpdatedAt()).isEqualTo(NOW.plusSeconds(60));

    skill.resubmitEvidence(NOW.plusSeconds(120));
    assertThat(skill.getStatus()).isEqualTo(OfferedSkillStatus.PENDING);
    assertThat(skill.getUpdatedAt()).isEqualTo(NOW.plusSeconds(120));
  }

  @Test
  @DisplayName("only a pending skill can be approved or rejected")
  void onlyAPendingSkillCanBeReviewed() {
    OfferedSkill rejected = OfferedSkill.requestValidation(UUID.randomUUID(), UPC, TUTOR, catalogItemId, NOW);
    rejected.rejectEvidence(NOW);
    OfferedSkill enabled = enabledSkill(catalogItemId, THRESHOLD);

    assertThatThrownBy(() -> rejected.approveByReviewedEvidence(NOW)).isInstanceOf(SkillsStateConflict.class);
    assertThatThrownBy(() -> rejected.rejectEvidence(NOW)).isInstanceOf(SkillsStateConflict.class);
    assertThatThrownBy(() -> enabled.rejectEvidence(NOW)).isInstanceOf(SkillsStateConflict.class);
    assertThatThrownBy(() -> enabled.approveByReviewedEvidence(NOW)).isInstanceOf(SkillsStateConflict.class);
  }

  @Test
  @DisplayName("only a rejected skill can be submitted again")
  void onlyARejectedSkillCanBeSubmittedAgain() {
    OfferedSkill pending = OfferedSkill.requestValidation(UUID.randomUUID(), UPC, TUTOR, catalogItemId, NOW);
    OfferedSkill enabled = enabledSkill(catalogItemId, THRESHOLD);

    assertThatThrownBy(() -> pending.resubmitEvidence(NOW)).isInstanceOf(SkillsStateConflict.class);
    assertThatThrownBy(() -> enabled.resubmitEvidence(NOW)).isInstanceOf(SkillsStateConflict.class);
  }

  @Test
  @DisplayName("a withdrawn tool with accepted evidence is offered again without a new review")
  void aWithdrawnToolIsOfferedAgainWithoutANewReview() {
    OfferedSkill skill = OfferedSkill.requestValidation(UUID.randomUUID(), UPC, TUTOR, catalogItemId, NOW);
    skill.approveByReviewedEvidence(NOW.plusSeconds(60));
    skill.withdraw(NOW.plusSeconds(120));

    skill.reEnableByReviewedEvidence(NOW.plusSeconds(180));

    assertThat(skill.isEnabled()).isTrue();
    assertThat(skill.getAccreditationPath()).isEqualTo(AccreditationPath.REVIEWED_EVIDENCE);
    assertThat(skill.getEnabledAt()).isEqualTo(NOW.plusSeconds(180));
    assertThat(skill.getUpdatedAt()).isEqualTo(NOW.plusSeconds(180));
  }

  @Test
  @DisplayName("only a withdrawn tool accredited by evidence is offered again this way")
  void onlyAWithdrawnToolAccreditedByEvidenceIsOfferedAgain() {
    OfferedSkill enabledTool = OfferedSkill.requestValidation(UUID.randomUUID(), UPC, TUTOR, catalogItemId, NOW);
    enabledTool.approveByReviewedEvidence(NOW);
    OfferedSkill withdrawnCourse = enabledSkill(catalogItemId, THRESHOLD);
    withdrawnCourse.withdraw(NOW);
    OfferedSkill rejectedTool = OfferedSkill.requestValidation(UUID.randomUUID(), UPC, TUTOR, catalogItemId, NOW);
    rejectedTool.rejectEvidence(NOW);

    assertThatThrownBy(() -> enabledTool.reEnableByReviewedEvidence(NOW)).isInstanceOf(SkillsStateConflict.class);
    assertThatThrownBy(() -> withdrawnCourse.reEnableByReviewedEvidence(NOW)).isInstanceOf(SkillsStateConflict.class);
    assertThatThrownBy(() -> rejectedTool.reEnableByReviewedEvidence(NOW)).isInstanceOf(SkillsStateConflict.class);
  }

  @Test
  @DisplayName("only a withdrawn skill can be enabled again")
  void onlyAWithdrawnSkillCanBeEnabledAgain() {
    OfferedSkill skill = enabledSkill(catalogItemId, THRESHOLD);

    assertThatThrownBy(
            () -> skill.reEnableByAcademicRecord(THRESHOLD, THRESHOLD, NOW.plusSeconds(60)))
        .isInstanceOf(SkillsStateConflict.class);
  }
}
