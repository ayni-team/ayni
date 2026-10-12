package pe.ayni.audit.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import pe.ayni.audit.domain.model.Severity;
import pe.ayni.audit.domain.services.RepeatedPairRule;

/** When a pair met more than is usual, without Spring. */
class RepeatedPairRuleTest {

  private final RepeatedPairRule rule = new RepeatedPairRule(3);

  @Test
  @DisplayName("Up to the threshold is usual, one more is not")
  void threshold() {
    assertThat(rule.exceeds(3)).isFalse();
    assertThat(rule.exceeds(4)).isTrue();
  }

  @Test
  @DisplayName("It weighs more when the pair doubles what is usual")
  void severity() {
    assertThat(rule.severityOf(4)).isEqualTo(Severity.MEDIUM);
    assertThat(rule.severityOf(5)).isEqualTo(Severity.MEDIUM);
    assertThat(rule.severityOf(6)).isEqualTo(Severity.HIGH);
  }

  @Test
  @DisplayName("A threshold below one session is refused")
  void invalidThreshold() {
    assertThatThrownBy(() -> new RepeatedPairRule(0)).isInstanceOf(IllegalArgumentException.class);
  }
}
