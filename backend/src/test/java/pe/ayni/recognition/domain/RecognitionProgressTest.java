package pe.ayni.recognition.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import pe.ayni.recognition.domain.model.RecognitionProgress;

/** US27: what the figures of the progress say, without Spring. */
class RecognitionProgressTest {

  @Test
  @DisplayName("below the requirement, the missing hours are the difference and the student cannot request yet")
  void belowTheRequirement() {
    RecognitionProgress progress = new RecognitionProgress(14, 9, 20);

    assertThat(progress.missingHours()).isEqualTo(6);
    assertThat(progress.requirementMet()).isFalse();
    assertThat(progress.ruleInForce()).isTrue();
  }

  @Test
  @DisplayName("exactly the requirement is met and nothing is missing")
  void exactlyTheRequirement() {
    RecognitionProgress progress = new RecognitionProgress(20, 12, 20);

    assertThat(progress.missingHours()).isZero();
    assertThat(progress.requirementMet()).isTrue();
  }

  @Test
  @DisplayName("above the requirement nothing is missing, never a negative number")
  void aboveTheRequirement() {
    RecognitionProgress progress = new RecognitionProgress(27, 15, 20);

    assertThat(progress.missingHours()).isZero();
    assertThat(progress.requirementMet()).isTrue();
  }

  @Test
  @DisplayName("without a rule there is nothing missing to tell and the requirement cannot be met")
  void withoutARule() {
    RecognitionProgress progress = new RecognitionProgress(100, 50, null);

    assertThat(progress.ruleInForce()).isFalse();
    assertThat(progress.missingHours()).isNull();
    assertThat(progress.requirementMet()).isFalse();
  }

  @Test
  @DisplayName("a student who has taught nothing misses all of it")
  void taughtNothing() {
    assertThat(new RecognitionProgress(0, 0, 20).missingHours()).isEqualTo(20);
  }

  @Test
  @DisplayName("negative hours or a requirement of zero are refused")
  void invalidFigures() {
    assertThatThrownBy(() -> new RecognitionProgress(-1, 0, 20)).isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new RecognitionProgress(0, -1, 20)).isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new RecognitionProgress(0, 0, 0)).isInstanceOf(IllegalArgumentException.class);
  }
}
