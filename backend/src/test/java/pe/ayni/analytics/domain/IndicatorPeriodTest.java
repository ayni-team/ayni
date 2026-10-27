package pe.ayni.analytics.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import pe.ayni.analytics.domain.model.IndicatorPeriod;
import pe.ayni.analytics.domain.model.InvalidPeriod;

class IndicatorPeriodTest {

  @Test
  void aSingleDayIsAPeriod() {
    LocalDate day = LocalDate.of(2026, 10, 5);

    assertThat(new IndicatorPeriod(day, day).days()).containsExactly(day);
  }

  @Test
  void bothEndsAreIncluded() {
    IndicatorPeriod period = new IndicatorPeriod(LocalDate.of(2026, 10, 30), LocalDate.of(2026, 11, 2));

    assertThat(period.days())
        .containsExactly(
            LocalDate.of(2026, 10, 30),
            LocalDate.of(2026, 10, 31),
            LocalDate.of(2026, 11, 1),
            LocalDate.of(2026, 11, 2));
  }

  @Test
  void aPeriodThatEndsBeforeItStartsIsRefused() {
    assertThatThrownBy(() -> new IndicatorPeriod(LocalDate.of(2026, 10, 6), LocalDate.of(2026, 10, 5)))
        .isInstanceOf(InvalidPeriod.class)
        .hasMessageContaining("before");
  }

  @Test
  void aPeriodOfAYearIsTheLongestAllowed() {
    LocalDate start = LocalDate.of(2026, 1, 1);

    assertThat(new IndicatorPeriod(start, start.plusDays(365)).days()).hasSize(366);
    assertThatThrownBy(() -> new IndicatorPeriod(start, start.plusDays(366)))
        .isInstanceOf(InvalidPeriod.class)
        .hasMessageContaining("366");
  }

  @Test
  void bothEndsAreRequired() {
    assertThatThrownBy(() -> new IndicatorPeriod(null, LocalDate.of(2026, 10, 5)))
        .isInstanceOf(InvalidPeriod.class);
    assertThatThrownBy(() -> new IndicatorPeriod(LocalDate.of(2026, 10, 5), null))
        .isInstanceOf(InvalidPeriod.class);
  }
}
