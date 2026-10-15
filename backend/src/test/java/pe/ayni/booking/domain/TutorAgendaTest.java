package pe.ayni.booking.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import pe.ayni.booking.domain.model.AgendaPeriod;
import pe.ayni.booking.domain.model.Booking;
import pe.ayni.booking.domain.model.BookingRuleViolation;
import pe.ayni.booking.domain.model.HourBlock;
import pe.ayni.booking.domain.services.TutorAgenda;

/** The agenda of US21, without Spring: what is booked, what is free, and the days it covers. */
class TutorAgendaTest {

  private static final ZoneId LIMA = ZoneId.of("America/Lima");
  private static final UUID TUTOR = UUID.randomUUID();
  private static final LocalDate MONDAY = LocalDate.of(2026, 10, 12);
  private static final Instant NOW = at(MONDAY, 8);
  private static final AgendaPeriod WEEK = new AgendaPeriod(MONDAY, MONDAY.plusDays(6), LIMA);

  private static Instant at(LocalDate day, int hour) {
    return ZonedDateTime.of(day, LocalTime.of(hour, 0), LIMA).toInstant();
  }

  private static HourBlock block(Instant start) {
    return new HourBlock(
        UUID.randomUUID(), "UPC", TUTOR, start, start.plus(Duration.ofHours(1)), null, NOW.minus(Duration.ofDays(3)));
  }

  private static Booking bookingOf(HourBlock... hours) {
    Instant start = hours[0].getStartsAt();
    Booking booking =
        Booking.confirm(
            UUID.randomUUID(),
            "UPC",
            UUID.randomUUID(),
            TUTOR,
            UUID.randomUUID(),
            start,
            start.plus(Duration.ofHours(hours.length)),
            hours.length,
            "Normal forms",
            NOW.minus(Duration.ofDays(1)));
    for (HourBlock hour : hours) {
      hour.hold(booking.getStudentId(), NOW.minus(Duration.ofDays(1)));
      hour.book(booking.getId(), booking.getStudentId(), NOW.minus(Duration.ofDays(1)));
    }
    return booking;
  }

  @Test
  @DisplayName("A booking appears once and the free hours around it in order")
  void bookedOnceAndFreeAround() {
    HourBlock nine = block(at(MONDAY.plusDays(1), 9));
    HourBlock ten = block(at(MONDAY.plusDays(1), 10));
    HourBlock eleven = block(at(MONDAY.plusDays(1), 11));
    HourBlock held = block(at(MONDAY.plusDays(2), 9));
    held.hold(UUID.randomUUID(), NOW);
    Booking twoHours = bookingOf(ten, eleven);

    List<TutorAgenda.Entry> agenda =
        TutorAgenda.of(List.of(twoHours), List.of(nine, ten, eleven, held), WEEK, NOW);

    assertThat(agenda).hasSize(3);
    assertThat(agenda.get(0)).isInstanceOf(TutorAgenda.Free.class);
    assertThat(agenda.get(0).startsAt()).isEqualTo(nine.getStartsAt());
    assertThat(agenda.get(1)).isEqualTo(new TutorAgenda.Booked(twoHours));
    assertThat(agenda.get(1).endsAt()).isEqualTo(eleven.getEndsAt());
    // A held hour is still nobody's: it is shown free, as booking offers it.
    assertThat(agenda.get(2)).isInstanceOf(TutorAgenda.Free.class);
  }

  @Test
  @DisplayName("A free hour that started, or one withdrawn, is not shown; a session in progress is")
  void startedFreeHoursAndWithdrawnOnesAreNotShown() {
    HourBlock earlier = block(at(MONDAY, 7));
    HourBlock withdrawn = block(at(MONDAY, 10));
    withdrawn.withdraw();
    HourBlock now = block(at(MONDAY, 8));
    Booking inProgress = bookingOf(now);
    Instant halfPastEight = NOW.plus(Duration.ofMinutes(30));

    List<TutorAgenda.Entry> agenda =
        TutorAgenda.of(List.of(inProgress), List.of(earlier, withdrawn, now), WEEK, halfPastEight);

    assertThat(agenda).containsExactly(new TutorAgenda.Booked(inProgress));
  }

  @Test
  @DisplayName("A cancelled booking, or one that ended, is not on the agenda")
  void cancelledOrEndedIsNotOnTheAgenda() {
    Booking cancelled = bookingOf(block(at(MONDAY.plusDays(1), 9)));
    cancelled.cancel(pe.ayni.booking.domain.model.CancelledBy.TUTOR, "Plans changed", NOW);
    Booking ended = bookingOf(block(at(MONDAY, 6)));

    assertThat(TutorAgenda.of(List.of(cancelled, ended), List.of(), WEEK, NOW)).isEmpty();
  }

  @Test
  @DisplayName("The days are the university's: 22:00 in Lima is that day, not the next one in UTC")
  void theDaysAreTheUniversitys() {
    AgendaPeriod monday = new AgendaPeriod(MONDAY, MONDAY, LIMA);
    HourBlock tenAtNight = block(at(MONDAY, 22));
    HourBlock tuesdayMidnight = block(at(MONDAY.plusDays(1), 0));

    List<TutorAgenda.Entry> agenda =
        TutorAgenda.of(List.of(), List.of(tenAtNight, tuesdayMidnight), monday, NOW);

    assertThat(agenda).extracting(TutorAgenda.Entry::startsAt).containsExactly(tenAtNight.getStartsAt());
    assertThat(monday.start()).isEqualTo(Instant.parse("2026-10-12T05:00:00Z"));
    assertThat(monday.end()).isEqualTo(Instant.parse("2026-10-13T05:00:00Z"));
  }

  @Test
  @DisplayName("Without days, the agenda is a week from today in the university's zone")
  void defaultPeriod() {
    Instant lateSundayInLima = Instant.parse("2026-10-12T03:00:00Z");

    AgendaPeriod period = AgendaPeriod.requested(null, null, LIMA, lateSundayInLima);

    assertThat(period.from()).isEqualTo(LocalDate.of(2026, 10, 11));
    assertThat(period.to()).isEqualTo(LocalDate.of(2026, 10, 17));
    assertThat(AgendaPeriod.requested(MONDAY, null, LIMA, NOW).to()).isEqualTo(MONDAY.plusDays(6));
  }

  @Test
  @DisplayName("A period backwards, or longer than a month, is refused")
  void refusedPeriods() {
    assertThatThrownBy(() -> new AgendaPeriod(MONDAY, MONDAY.minusDays(1), LIMA))
        .isInstanceOf(BookingRuleViolation.class);
    assertThatThrownBy(() -> new AgendaPeriod(MONDAY, MONDAY.plusDays(31), LIMA))
        .isInstanceOf(BookingRuleViolation.class);
    assertThat(new AgendaPeriod(MONDAY, MONDAY.plusDays(30), LIMA).to()).isEqualTo(MONDAY.plusDays(30));
  }
}
