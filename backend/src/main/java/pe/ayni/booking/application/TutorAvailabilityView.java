package pe.ayni.booking.application;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;
import pe.ayni.booking.domain.model.ExceptionKind;

/**
 * What a tutor declared about their availability and still governs their hours (US19, US20, US22):
 * the rules the hours are generated from, not the hours themselves.
 *
 * @param timezone the university's, in which every date and time below is
 */
public record TutorAvailabilityView(
    String timezone,
    List<WeeklyRange> patterns,
    List<DateException> exceptions,
    List<Pause> pauses) {

  /**
   * A weekly range.
   *
   * @param validUntil {@code null} for a range with no end date
   */
  public record WeeklyRange(
      UUID id,
      DayOfWeek dayOfWeek,
      LocalTime startsAtTime,
      LocalTime endsAtTime,
      LocalDate validFrom,
      LocalDate validUntil) {}

  /**
   * A change to one date.
   *
   * @param startsAtTime and {@code endsAtTime}: both {@code null} for a REMOVE of the whole day
   */
  public record DateException(
      UUID id, LocalDate exceptionDate, LocalTime startsAtTime, LocalTime endsAtTime,
      ExceptionKind kind) {}

  /** A period away, both days included. */
  public record Pause(UUID id, LocalDate startsOn, LocalDate endsOn) {}
}
