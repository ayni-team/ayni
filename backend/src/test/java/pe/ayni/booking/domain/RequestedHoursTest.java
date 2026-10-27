package pe.ayni.booking.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static pe.ayni.booking.domain.BookingFixtures.LIMA;
import static pe.ayni.booking.domain.BookingFixtures.NOW;
import static pe.ayni.booking.domain.BookingFixtures.TUTOR;
import static pe.ayni.booking.domain.BookingFixtures.UPC;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import pe.ayni.booking.domain.model.BookingRuleViolation;
import pe.ayni.booking.domain.model.HourBlock;
import pe.ayni.booking.domain.model.HoursNotOffered;
import pe.ayni.booking.domain.services.RequestedHours;

/** Consecutive means consecutive: as many hours as asked for, from where asked, with no gap. */
class RequestedHoursTest {

  private static final LocalDate TOMORROW = LocalDate.of(2026, 9, 21);

  private static Instant at(LocalDate day, int hour, int minute) {
    return ZonedDateTime.of(day, LocalTime.of(hour, minute), LIMA).toInstant();
  }

  private static HourBlock hourFrom(Instant start) {
    return new HourBlock(
        UUID.randomUUID(), UPC, TUTOR, start, start.plus(Duration.ofHours(1)), null, NOW);
  }

  @Test
  @DisplayName("consecutive hours are accepted, whatever order they are found in")
  void acceptsConsecutiveHours() {

    HourBlock nine = hourFrom(at(TOMORROW, 9, 0));
    HourBlock ten = hourFrom(at(TOMORROW, 10, 0));
    HourBlock eleven = hourFrom(at(TOMORROW, 11, 0));

    RequestedHours requested =
        RequestedHours.of(List.of(eleven, nine, ten), at(TOMORROW, 9, 0), 3);

    assertThat(requested.blocks()).containsExactly(nine, ten, eleven);
    assertThat(requested.hours()).isEqualTo(3);
    assertThat(requested.startsAt()).isEqualTo(at(TOMORROW, 9, 0));
    assertThat(requested.endsAt()).isEqualTo(at(TOMORROW, 12, 0));
    assertThat(requested.blockIds()).containsExactly(nine.getId(), ten.getId(), eleven.getId());
  }

  @Test
  @DisplayName("a stretch across midnight is still consecutive")
  void acceptsAStretchAcrossMidnight() {

    HourBlock eleven = hourFrom(at(TOMORROW, 23, 0));
    HourBlock midnight = hourFrom(at(TOMORROW.plusDays(1), 0, 0));

    RequestedHours requested = RequestedHours.of(List.of(eleven, midnight), eleven.getStartsAt(), 2);

    assertThat(requested.endsAt()).isEqualTo(at(TOMORROW.plusDays(1), 1, 0));
    assertThat(RequestedHours.endOf(eleven.getStartsAt(), 2)).isEqualTo(requested.endsAt());
  }

  @Test
  @DisplayName("an hour the tutor does not offer is refused")
  void refusesAMissingHour() {

    HourBlock nine = hourFrom(at(TOMORROW, 9, 0));
    HourBlock eleven = hourFrom(at(TOMORROW, 11, 0));

    assertThatThrownBy(() -> RequestedHours.of(List.of(nine, eleven), nine.getStartsAt(), 3))
        .isInstanceOf(HoursNotOffered.class)
        .hasMessageContaining("3 consecutive hours");
  }

  @Test
  @DisplayName("hours with a gap between them are not consecutive")
  void refusesAGap() {

    // Two windows of the tutor, one starting on the hour and one on the half hour: both fall in
    // the range asked for, but they do not follow one another.
    HourBlock nine = hourFrom(at(TOMORROW, 9, 0));
    HourBlock halfPastTen = hourFrom(at(TOMORROW, 10, 30));

    assertThatThrownBy(() -> RequestedHours.of(List.of(nine, halfPastTen), nine.getStartsAt(), 2))
        .isInstanceOf(HoursNotOffered.class);
  }

  @Test
  @DisplayName("the stretch has to start where the student said")
  void refusesAStretchStartingElsewhere() {

    HourBlock halfPastNine = hourFrom(at(TOMORROW, 9, 30));

    assertThatThrownBy(
            () -> RequestedHours.of(List.of(halfPastNine), at(TOMORROW, 9, 0), 1))
        .isInstanceOf(HoursNotOffered.class)
        .hasMessageContaining("the hour starting at");
  }

  @Test
  @DisplayName("nothing found is nothing offered")
  void refusesWhenNothingIsFound() {
    assertThatThrownBy(() -> RequestedHours.of(List.of(), at(TOMORROW, 9, 0), 1))
        .isInstanceOf(HoursNotOffered.class);
  }

  @Test
  @DisplayName("asking for no hours is not a request")
  void refusesZeroHours() {
    assertThatThrownBy(() -> RequestedHours.endOf(at(TOMORROW, 9, 0), 0))
        .isInstanceOf(BookingRuleViolation.class);
    assertThatThrownBy(() -> RequestedHours.of(List.of(), at(TOMORROW, 9, 0), 0))
        .isInstanceOf(BookingRuleViolation.class);
  }
}
