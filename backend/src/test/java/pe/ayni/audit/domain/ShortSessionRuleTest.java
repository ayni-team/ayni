package pe.ayni.audit.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import pe.ayni.audit.domain.model.Severity;
import pe.ayni.audit.domain.services.ShortSessionRule;

/** When a session lasted too little, without Spring. */
class ShortSessionRuleTest {

  private static final Duration HOUR = Duration.ofHours(1);

  private final ShortSessionRule half = new ShortSessionRule(0.5);

  @Test
  @DisplayName("Less than the fraction of the booked time is short, and the fraction itself is not")
  void boundary() {
    assertThat(half.isShort(HOUR, Duration.ofMinutes(29))).isTrue();
    assertThat(half.isShort(HOUR, Duration.ofMinutes(30))).isFalse();
    assertThat(half.isShort(HOUR, Duration.ofMinutes(60))).isFalse();
    assertThat(half.isShort(Duration.ofHours(2), Duration.ofMinutes(59))).isTrue();
  }

  @Test
  @DisplayName("The fraction is configurable")
  void configurable() {
    ShortSessionRule threeQuarters = new ShortSessionRule(0.75);

    assertThat(threeQuarters.isShort(HOUR, Duration.ofMinutes(40))).isTrue();
    assertThat(half.isShort(HOUR, Duration.ofMinutes(40))).isFalse();
  }

  @Test
  @DisplayName("Without a booked time or a duration there is nothing to judge")
  void nothingToJudge() {
    assertThat(half.isShort(Duration.ZERO, Duration.ZERO)).isFalse();
    assertThat(half.isShort(null, Duration.ofMinutes(5))).isFalse();
    assertThat(half.isShort(HOUR, null)).isFalse();
  }

  @Test
  @DisplayName("It weighs more when it did not even reach half of the fraction")
  void severity() {
    assertThat(half.severityOf(HOUR, Duration.ofMinutes(20))).isEqualTo(Severity.MEDIUM);
    assertThat(half.severityOf(HOUR, Duration.ofMinutes(14))).isEqualTo(Severity.HIGH);
  }

  @Test
  @DisplayName("A fraction outside of 0 to 1 is refused")
  void invalidFraction() {
    assertThatThrownBy(() -> new ShortSessionRule(0)).isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new ShortSessionRule(1.5)).isInstanceOf(IllegalArgumentException.class);
    assertThat(new ShortSessionRule(1).isShort(HOUR, Duration.ofMinutes(59))).isTrue();
  }
}
