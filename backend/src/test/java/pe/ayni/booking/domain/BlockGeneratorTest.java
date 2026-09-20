package pe.ayni.booking.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static pe.ayni.booking.domain.BookingFixtures.LIMA;
import static pe.ayni.booking.domain.BookingFixtures.MONDAY;
import static pe.ayni.booking.domain.BookingFixtures.NOW;
import static pe.ayni.booking.domain.BookingFixtures.addWindow;
import static pe.ayni.booking.domain.BookingFixtures.pattern;
import static pe.ayni.booking.domain.BookingFixtures.patternValid;
import static pe.ayni.booking.domain.BookingFixtures.pause;
import static pe.ayni.booking.domain.BookingFixtures.removeWholeDay;
import static pe.ayni.booking.domain.BookingFixtures.removeWindow;
import static pe.ayni.booking.domain.BookingFixtures.startTimesOf;

import java.time.Duration;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import pe.ayni.booking.domain.model.AvailabilityPattern;
import pe.ayni.booking.domain.model.BookingRuleViolation;
import pe.ayni.booking.domain.model.HourBlock;
import pe.ayni.booking.domain.services.BlockGenerator;

/** What a tutor's weekly rules turn into once they meet the calendar. */
class BlockGeneratorTest {

  private final BlockGenerator generator = new BlockGenerator();

  private List<HourBlock> generate(
      List<AvailabilityPattern> patterns,
      List<pe.ayni.booking.domain.model.AvailabilityException> exceptions,
      List<pe.ayni.booking.domain.model.AvailabilityPause> pauses) {
    return generator.generate(patterns, exceptions, pauses, MONDAY, MONDAY, LIMA, NOW);
  }

  @Test
  @DisplayName("a window becomes one block per whole hour inside it")
  void cutsTheWindowIntoWholeHours() {

    List<HourBlock> blocks =
        generate(List.of(pattern(LocalTime.of(9, 0), LocalTime.of(12, 0))), List.of(), List.of());

    assertThat(startTimesOf(blocks))
        .containsExactly(LocalTime.of(9, 0), LocalTime.of(10, 0), LocalTime.of(11, 0));
  }

  @Test
  @DisplayName("an hour that does not fit whole is not generated")
  void leavesTheRemainderAlone() {

    List<HourBlock> blocks =
        generate(List.of(pattern(LocalTime.of(9, 0), LocalTime.of(10, 30))), List.of(), List.of());

    // Half an hour is not bookable: one credit buys one hour.
    assertThat(startTimesOf(blocks)).containsExactly(LocalTime.of(9, 0));
  }

  @Test
  @DisplayName("a window that runs to the end of the evening finishes, and stops at its end")
  void finishesOnAWindowThatReachesTheLastHourOfTheDay() {

    // LocalTime.plusHours wraps around midnight, so walking the window with it never reached the
    // end and never stopped. A tutor free until eleven at night was enough to hang the generator.
    List<HourBlock> blocks =
        assertTimeoutPreemptively(
            Duration.ofSeconds(5),
            () ->
                generate(
                    List.of(pattern(LocalTime.of(20, 0), LocalTime.of(23, 0))),
                    List.of(),
                    List.of()));

    assertThat(startTimesOf(blocks))
        .containsExactly(LocalTime.of(20, 0), LocalTime.of(21, 0), LocalTime.of(22, 0));
  }

  @Test
  @DisplayName("a pause takes the whole day out")
  void aPauseRemovesTheDay() {

    List<HourBlock> blocks =
        generate(
            List.of(pattern(LocalTime.of(9, 0), LocalTime.of(12, 0))),
            List.of(),
            List.of(pause(MONDAY.minusDays(1), MONDAY.plusDays(3))));

    assertThat(blocks).isEmpty();
  }

  @Test
  @DisplayName("a REMOVE without times takes the whole day out")
  void aWholeDayRemovalRemovesTheDay() {

    List<HourBlock> blocks =
        generate(
            List.of(pattern(LocalTime.of(9, 0), LocalTime.of(12, 0))),
            List.of(removeWholeDay(MONDAY)),
            List.of());

    assertThat(blocks).isEmpty();
  }

  @Test
  @DisplayName("a REMOVE takes away every hour it runs through, not only the ones it covers whole")
  void aPartialRemovalTakesTheHoursItTouches() {

    List<HourBlock> blocks =
        generate(
            List.of(pattern(LocalTime.of(9, 0), LocalTime.of(13, 0))),
            List.of(removeWindow(MONDAY, LocalTime.of(10, 30), LocalTime.of(11, 30))),
            List.of());

    // The tutor said they cannot be there between half ten and half eleven. Offering ten o'clock
    // or eleven o'clock would book them into the middle of that.
    assertThat(startTimesOf(blocks)).containsExactly(LocalTime.of(9, 0), LocalTime.of(12, 0));
  }

  @Test
  @DisplayName("a REMOVE that only touches the edge of an hour leaves it alone")
  void aRemovalThatOnlyTouchesTheEdgeKeepsTheHour() {

    List<HourBlock> blocks =
        generate(
            List.of(pattern(LocalTime.of(9, 0), LocalTime.of(11, 0))),
            List.of(removeWindow(MONDAY, LocalTime.of(10, 0), LocalTime.of(11, 0))),
            List.of());

    // Ending where the next hour begins is not running through it.
    assertThat(startTimesOf(blocks)).containsExactly(LocalTime.of(9, 0));
  }

  @Test
  @DisplayName("an ADD puts in hours the patterns never described")
  void anAdditionAddsExtraordinaryHours() {

    List<HourBlock> blocks =
        generate(
            List.of(pattern(LocalTime.of(9, 0), LocalTime.of(10, 0))),
            List.of(addWindow(MONDAY, LocalTime.of(18, 0), LocalTime.of(20, 0))),
            List.of());

    assertThat(startTimesOf(blocks))
        .containsExactly(LocalTime.of(9, 0), LocalTime.of(18, 0), LocalTime.of(19, 0));
  }

  @Test
  @DisplayName("an ADD over an hour a pattern already gives leaves one block, still tied to the pattern")
  void anAdditionOverAPatternHourDoesNotDuplicateIt() {

    AvailabilityPattern morning = pattern(LocalTime.of(9, 0), LocalTime.of(10, 0));

    List<HourBlock> blocks =
        generate(
            List.of(morning),
            List.of(addWindow(MONDAY, LocalTime.of(9, 0), LocalTime.of(10, 0))),
            List.of());

    // Two rows with the same starts_at would be refused by uq_hour_blocks_tenant_tutor_start the
    // moment anybody tried to save them.
    assertThat(blocks).hasSize(1);
    assertThat(blocks.getFirst().getPatternId()).isEqualTo(morning.getId());
  }

  @Test
  @DisplayName("a pattern outside its validity does not generate anything")
  void ignoresAPatternThatHasExpired() {

    List<HourBlock> blocks =
        generate(
            List.of(
                patternValid(
                    LocalTime.of(9, 0),
                    LocalTime.of(12, 0),
                    MONDAY.minusMonths(2),
                    MONDAY.minusDays(1))),
            List.of(),
            List.of());

    assertThat(blocks).isEmpty();
  }

  @Test
  @DisplayName("only the patterns of that weekday are used")
  void ignoresPatternsOfOtherWeekdays() {

    List<HourBlock> blocks =
        generator.generate(
            List.of(pattern(LocalTime.of(9, 0), LocalTime.of(11, 0))),
            List.of(),
            List.of(),
            MONDAY.plusDays(1),
            MONDAY.plusDays(1),
            LIMA,
            NOW);

    assertThat(blocks).isEmpty();
  }

  @Test
  @DisplayName("every block lasts exactly one hour, including the night the clocks change")
  void everyBlockLastsAnHour() {

    ZoneId madrid = ZoneId.of("Europe/Madrid");
    var clocksChange = java.time.LocalDate.of(2026, 10, 25);

    List<HourBlock> blocks =
        generator.generate(
            List.of(
                new AvailabilityPattern(
                    java.util.UUID.randomUUID(),
                    BookingFixtures.UPC,
                    BookingFixtures.TUTOR,
                    clocksChange.getDayOfWeek(),
                    LocalTime.of(1, 0),
                    LocalTime.of(5, 0),
                    clocksChange.minusMonths(1),
                    null,
                    NOW)),
            List.of(),
            List.of(),
            clocksChange,
            clocksChange,
            madrid,
            NOW);

    // One credit buys one hour, so an hour has to be an hour even on the night an hour repeats.
    assertThat(blocks).isNotEmpty();
    assertThat(blocks)
        .allSatisfy(
            block ->
                assertThat(Duration.between(block.getStartsAt(), block.getEndsAt()))
                    .isEqualTo(Duration.ofHours(1)));
    assertThat(blocks).extracting(HourBlock::getStartsAt).doesNotHaveDuplicates();
  }

  @Test
  @DisplayName("refuses a range that ends before it starts")
  void refusesABackwardsRange() {
    assertThatThrownBy(
            () ->
                generator.generate(
                    List.of(), List.of(), List.of(), MONDAY, MONDAY.minusDays(1), LIMA, NOW))
        .isInstanceOf(BookingRuleViolation.class);
  }
}
