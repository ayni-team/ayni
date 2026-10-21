package pe.ayni.recognition.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import pe.ayni.recognition.domain.model.RecognitionRule;
import pe.ayni.recognition.domain.model.RecognitionRuleViolation;

/** US27, US28: what a coordinator may set as the rule, and which rules the new one replaces, without Spring. */
class RecognitionRuleTest {

  private static final Instant NOW = Instant.parse("2026-10-20T15:00:00Z");
  private static final LocalDate TODAY = LocalDate.of(2026, 10, 20);

  private static RecognitionRule starting(int hours, LocalDate from) {
    return RecognitionRule.start(UUID.randomUUID(), "UPC", hours, from, TODAY, NOW);
  }

  @Test
  @DisplayName("a rule without a day starts today, in the university's calendar")
  void startsToday() {
    RecognitionRule rule = starting(20, null);

    assertThat(rule.getValidFrom()).isEqualTo(TODAY);
    assertThat(rule.getMinimumHours()).isEqualTo(20);
    assertThat(rule.getMinimumRating()).isNull();
    assertThat(rule.isInForceOn(TODAY)).isTrue();
  }

  @Test
  @DisplayName("a rule may start on a later day and is not in force before it")
  void startsLater() {
    RecognitionRule rule = starting(20, TODAY.plusDays(7));

    assertThat(rule.isInForceOn(TODAY)).isFalse();
    assertThat(rule.isInForceOn(TODAY.plusDays(7))).isTrue();
  }

  @Test
  @DisplayName("fewer than one hour is refused")
  void atLeastOneHour() {
    assertThatThrownBy(() -> starting(0, null)).isInstanceOf(RecognitionRuleViolation.class);
    assertThatThrownBy(() -> starting(-3, null)).isInstanceOf(RecognitionRuleViolation.class);
    assertThat(starting(1, null).getMinimumHours()).isEqualTo(1);
  }

  @Test
  @DisplayName("a day that already passed is refused")
  void notInThePast() {
    assertThatThrownBy(() -> starting(20, TODAY.minusDays(1))).isInstanceOf(RecognitionRuleViolation.class);
  }

  @Test
  @DisplayName("a rule that starts today replaces everything open: the one in force and the ones that start later")
  void startingTodayReplacesAllOpen() {
    RecognitionRule inForce = starting(10, TODAY);
    RecognitionRule scheduled = starting(15, TODAY.plusDays(3));

    assertThat(inForce.isReplacedBy(TODAY, TODAY)).isTrue();
    assertThat(scheduled.isReplacedBy(TODAY, TODAY)).isTrue();
  }

  @Test
  @DisplayName("a rule that starts later leaves the ones that start before it running")
  void startingLaterLeavesTheCurrentRunning() {
    RecognitionRule inForce = starting(10, TODAY);
    RecognitionRule sameDay = starting(15, TODAY.plusDays(7));
    RecognitionRule after = starting(25, TODAY.plusDays(14));

    assertThat(inForce.isReplacedBy(TODAY.plusDays(7), TODAY)).isFalse();
    assertThat(sameDay.isReplacedBy(TODAY.plusDays(7), TODAY)).isTrue();
    assertThat(after.isReplacedBy(TODAY.plusDays(7), TODAY)).isTrue();
  }

  @Test
  @DisplayName("a rule already replaced is not replaced again")
  void aReplacedRuleStaysReplaced() {
    RecognitionRule rule = starting(10, TODAY);
    rule.supersede(NOW);

    assertThat(rule.isReplacedBy(TODAY, TODAY)).isFalse();
  }
}
