package pe.ayni.matching.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import pe.ayni.matching.domain.model.MatchingRuleViolation;
import pe.ayni.matching.domain.model.SearchWindow;

class SearchWindowTest {

  private static final Instant TWO_PM = Instant.parse("2026-09-30T19:00:00Z");
  private static final Instant SIX_PM = Instant.parse("2026-09-30T23:00:00Z");

  @Test
  @DisplayName("a window must end after it starts")
  void aWindowMustEndAfterItStarts() {

    assertThatThrownBy(() -> new SearchWindow(SIX_PM, TWO_PM))
        .isInstanceOf(MatchingRuleViolation.class)
        .hasMessage("from must be before to");
    assertThatThrownBy(() -> new SearchWindow(TWO_PM, TWO_PM))
        .isInstanceOf(MatchingRuleViolation.class);
  }

  @Test
  @DisplayName("distance is measured to the edge the hour fell outside of, and the end is excluded")
  void distanceIsMeasuredToTheNearestEdge() {

    SearchWindow afternoon = new SearchWindow(TWO_PM, SIX_PM);

    assertThat(afternoon.distanceTo(TWO_PM)).isZero();
    assertThat(afternoon.distanceTo(TWO_PM.plus(Duration.ofHours(3)))).isZero();
    assertThat(afternoon.distanceTo(TWO_PM.minus(Duration.ofHours(2))))
        .isEqualTo(Duration.ofHours(2));
    // Six o'clock is not in [14:00, 18:00), but it is right at the edge.
    assertThat(afternoon.distanceTo(SIX_PM)).isZero();
    assertThat(afternoon.distanceTo(SIX_PM.plus(Duration.ofHours(1))))
        .isEqualTo(Duration.ofHours(1));
  }
}
