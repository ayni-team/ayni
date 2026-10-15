package pe.ayni.booking.application;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

/**
 * What a tutor has ahead in a period (US21), with each line also in the university's own zone.
 *
 * @param from first day of the period, in the university's zone
 * @param to last day of the period, included
 */
public record TutorAgendaView(LocalDate from, LocalDate to, String timezone, List<Line> lines) {

  /** Whether a line is a session booked with the tutor or an hour still free. */
  public enum Kind {
    BOOKED,
    FREE
  }

  /**
   * One line of the agenda: the UTC instants, the same moments in the university's zone, and the
   * booking when there is one.
   *
   * @param booking {@code null} for a free hour
   */
  public record Line(
      Kind kind,
      Instant startsAt,
      Instant endsAt,
      LocalDate date,
      LocalTime startTime,
      LocalTime endTime,
      TutorBooking booking) {}
}
