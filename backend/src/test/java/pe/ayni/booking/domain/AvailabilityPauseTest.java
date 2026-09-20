package pe.ayni.booking.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static pe.ayni.booking.domain.BookingFixtures.MONDAY;
import static pe.ayni.booking.domain.BookingFixtures.pause;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import pe.ayni.booking.domain.model.AvailabilityPause;
import pe.ayni.booking.domain.model.BookingRuleViolation;

/** Which days a tutor is away for. */
class AvailabilityPauseTest {

  @Test
  @DisplayName("covers both of the days it is written between")
  void coversBothEnds() {

    AvailabilityPause away = pause(MONDAY, MONDAY.plusDays(6));

    assertThat(away.includes(MONDAY)).isTrue();
    assertThat(away.includes(MONDAY.plusDays(6))).isTrue();
    assertThat(away.includes(MONDAY.plusDays(3))).isTrue();
  }

  @Test
  @DisplayName("does not cover the days either side of it")
  void doesNotCoverTheDaysAround() {

    AvailabilityPause away = pause(MONDAY, MONDAY.plusDays(6));

    assertThat(away.includes(MONDAY.minusDays(1))).isFalse();
    assertThat(away.includes(MONDAY.plusDays(7))).isFalse();
  }

  @Test
  @DisplayName("a pause of one day covers that day")
  void aSingleDayPauseCoversItsDay() {
    assertThat(pause(MONDAY, MONDAY).includes(MONDAY)).isTrue();
  }

  @Test
  @DisplayName("a pause that ends before it starts is refused")
  void refusesABackwardsPause() {
    assertThatThrownBy(() -> pause(MONDAY, MONDAY.minusDays(1)))
        .isInstanceOf(BookingRuleViolation.class)
        .hasMessageContaining("ends_on");
  }
}
