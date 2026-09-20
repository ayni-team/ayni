package pe.ayni.booking.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static pe.ayni.booking.domain.BookingFixtures.MONDAY;
import static pe.ayni.booking.domain.BookingFixtures.NOW;
import static pe.ayni.booking.domain.BookingFixtures.TUTOR;
import static pe.ayni.booking.domain.BookingFixtures.UPC;
import static pe.ayni.booking.domain.BookingFixtures.addWindow;
import static pe.ayni.booking.domain.BookingFixtures.removeWholeDay;
import static pe.ayni.booking.domain.BookingFixtures.removeWindow;

import java.time.LocalTime;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import pe.ayni.booking.domain.model.AvailabilityException;
import pe.ayni.booking.domain.model.BookingRuleViolation;
import pe.ayni.booking.domain.model.ExceptionKind;

/** What a date specific deviation is allowed to say. */
class AvailabilityExceptionTest {

  @Test
  @DisplayName("a REMOVE without times is about the whole day")
  void aRemovalWithoutTimesCoversTheDay() {

    AvailabilityException wholeDay = removeWholeDay(MONDAY);

    assertThat(wholeDay.removesTheWholeDay()).isTrue();
    assertThat(wholeDay.coversAWindow()).isFalse();
  }

  @Test
  @DisplayName("a REMOVE with times is about that window only")
  void aRemovalWithTimesCoversAWindow() {

    AvailabilityException window =
        removeWindow(MONDAY, LocalTime.of(10, 0), LocalTime.of(12, 0));

    assertThat(window.removesTheWholeDay()).isFalse();
    assertThat(window.coversAWindow()).isTrue();
  }

  @Test
  @DisplayName("an ADD needs to say when")
  void anAdditionRequiresTimes() {
    assertThatThrownBy(
            () ->
                new AvailabilityException(
                    UUID.randomUUID(), UPC, TUTOR, MONDAY, null, null, ExceptionKind.ADD, NOW))
        .isInstanceOf(BookingRuleViolation.class)
        .hasMessageContaining("ADD");
  }

  @Test
  @DisplayName("half a window is refused, whatever the kind")
  void refusesHalfAWindow() {

    // A start with no end leaves everything downstream guessing what the missing half meant. A
    // whole day removal says so by carrying no times at all.
    assertThatThrownBy(
            () ->
                new AvailabilityException(
                    UUID.randomUUID(),
                    UPC,
                    TUTOR,
                    MONDAY,
                    LocalTime.of(10, 0),
                    null,
                    ExceptionKind.REMOVE,
                    NOW))
        .isInstanceOf(BookingRuleViolation.class)
        .hasMessageContaining("both");
  }

  @Test
  @DisplayName("a window that ends before it starts is refused")
  void refusesABackwardsWindow() {
    assertThatThrownBy(() -> addWindow(MONDAY, LocalTime.of(12, 0), LocalTime.of(10, 0)))
        .isInstanceOf(BookingRuleViolation.class)
        .hasMessageContaining("ends_at_time");
  }
}
