package pe.ayni.booking.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static pe.ayni.booking.domain.BookingFixtures.MONDAY;
import static pe.ayni.booking.domain.BookingFixtures.NOW;
import static pe.ayni.booking.domain.BookingFixtures.TUTOR;
import static pe.ayni.booking.domain.BookingFixtures.UPC;
import static pe.ayni.booking.domain.BookingFixtures.pattern;
import static pe.ayni.booking.domain.BookingFixtures.patternValid;

import java.time.LocalTime;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import pe.ayni.booking.domain.model.AvailabilityPattern;
import pe.ayni.booking.domain.model.BookingRuleViolation;

/** When two of a tutor's weekly windows are the same window. */
class AvailabilityPatternTest {

  @Test
  @DisplayName("two windows of the same day that share hours collide")
  void overlappingHoursOnTheSameDayCollide() {
    assertThat(
            pattern(LocalTime.of(9, 0), LocalTime.of(12, 0))
                .overlaps(pattern(LocalTime.of(11, 0), LocalTime.of(13, 0))))
        .isTrue();
  }

  @Test
  @DisplayName("windows that meet at the edge do not collide")
  void consecutiveWindowsDoNotCollide() {
    // Ending at five and starting at five describes two hours in a row, not the same hour twice.
    assertThat(
            pattern(LocalTime.of(15, 0), LocalTime.of(17, 0))
                .overlaps(pattern(LocalTime.of(17, 0), LocalTime.of(19, 0))))
        .isFalse();
  }

  @Test
  @DisplayName("the same hours on a different weekday do not collide")
  void differentWeekdaysDoNotCollide() {

    AvailabilityPattern onMonday = pattern(LocalTime.of(9, 0), LocalTime.of(12, 0));
    AvailabilityPattern onTuesday =
        new AvailabilityPattern(
            UUID.randomUUID(),
            UPC,
            TUTOR,
            MONDAY.plusDays(1).getDayOfWeek(),
            LocalTime.of(9, 0),
            LocalTime.of(12, 0),
            MONDAY.minusMonths(1),
            null,
            NOW);

    assertThat(onMonday.overlaps(onTuesday)).isFalse();
  }

  @Test
  @DisplayName("the same hours in validities that never meet do not collide")
  void separateValiditiesDoNotCollide() {

    AvailabilityPattern lastTerm =
        patternValid(
            LocalTime.of(9, 0), LocalTime.of(12, 0), MONDAY.minusMonths(4), MONDAY.minusMonths(2));
    AvailabilityPattern thisTerm =
        patternValid(LocalTime.of(9, 0), LocalTime.of(12, 0), MONDAY.minusMonths(1), null);

    assertThat(lastTerm.overlaps(thisTerm)).isFalse();
  }

  @Test
  @DisplayName("validities that touch on a single day do collide there")
  void validitiesTouchingOnOneDayCollide() {

    AvailabilityPattern ending =
        patternValid(LocalTime.of(9, 0), LocalTime.of(12, 0), MONDAY.minusMonths(2), MONDAY);
    AvailabilityPattern starting =
        patternValid(LocalTime.of(10, 0), LocalTime.of(13, 0), MONDAY, null);

    // Dates are inclusive: on that Monday both windows are in force.
    assertThat(ending.overlaps(starting)).isTrue();
  }

  @Test
  @DisplayName("a window that never opens is refused")
  void refusesAWindowThatClosesBeforeItOpens() {
    assertThatThrownBy(() -> pattern(LocalTime.of(12, 0), LocalTime.of(9, 0)))
        .isInstanceOf(BookingRuleViolation.class)
        .hasMessageContaining("ends_at_time");
  }

  @Test
  @DisplayName("a validity that ends before it starts is refused")
  void refusesAValidityThatEndsBeforeItStarts() {
    assertThatThrownBy(
            () ->
                patternValid(
                    LocalTime.of(9, 0), LocalTime.of(12, 0), MONDAY, MONDAY.minusDays(1)))
        .isInstanceOf(BookingRuleViolation.class)
        .hasMessageContaining("valid_until");
  }
}
