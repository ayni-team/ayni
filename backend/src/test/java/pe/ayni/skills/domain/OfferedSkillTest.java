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
        .isInstanceOf(SkillsRuleViolation.class);
  }
}
