package pe.ayni.booking.interfaces.rest;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;
import pe.ayni.booking.application.TutorAvailabilityView;
import pe.ayni.booking.domain.model.ExceptionKind;

/** What the tutor declared about their availability and still governs their hours. */
@Schema(name = "TutorAvailability")
public record TutorAvailabilityResponse(
    @Schema(description = "Time zone of the university, in which every date and time is.",
        example = "America/Lima")
    String timezone,
    @Schema(description = "Weekly ranges still in force, by day of the week and hour.")
    List<WeeklyRange> patterns,
    @Schema(description = "Date exceptions from today on, by date and hour.")
    List<DateException> exceptions,
    @Schema(description = "The pause under way and the ones to come, by first day.")
    List<Pause> pauses) {

  /** A weekly range the tutor declared. */
  @Schema(name = "TutorAvailabilityWeeklyRange")
  public record WeeklyRange(
      @Schema(description = "Identifier to remove the range with.",
          example = "a0000000-0000-4000-8000-000000000001")
      UUID id,
      @Schema(example = "MONDAY") DayOfWeek dayOfWeek,
      @Schema(type = "string", example = "17:00:00") LocalTime startsAtTime,
      @Schema(type = "string", example = "19:00:00") LocalTime endsAtTime,
      @Schema(example = "2026-09-28") LocalDate validFrom,
      @Schema(description = "Last day of the range; null means no end date.",
          example = "2026-12-18", nullable = true)
      LocalDate validUntil) {}

  /** A change the tutor made to one date. */
  @Schema(name = "TutorAvailabilityException")
  public record DateException(
      @Schema(example = "b0000000-0000-4000-8000-000000000002") UUID id,
      @Schema(example = "2026-10-30") LocalDate exceptionDate,
      @Schema(description = "Null together with endsAtTime for a REMOVE of the whole day.",
          type = "string", example = "10:00:00", nullable = true)
      LocalTime startsAtTime,
      @Schema(description = "Null together with startsAtTime for a REMOVE of the whole day.",
          type = "string", example = "11:00:00", nullable = true)
      LocalTime endsAtTime,
      @Schema(description = "ADD gives extra hours on that date, REMOVE takes them away.",
          example = "REMOVE")
      ExceptionKind kind) {}

  /** A period the tutor is away, both days included. */
  @Schema(name = "TutorAvailabilityPause")
  public record Pause(
      @Schema(description = "Identifier to end the pause early with, once it is under way.",
          example = "c0000000-0000-4000-8000-000000000003")
      UUID id,
      @Schema(example = "2026-11-02") LocalDate startsOn,
      @Schema(example = "2026-11-08") LocalDate endsOn) {}

  static TutorAvailabilityResponse of(TutorAvailabilityView view) {
    return new TutorAvailabilityResponse(
        view.timezone(),
        view.patterns().stream()
            .map(
                range ->
                    new WeeklyRange(
                        range.id(),
                        range.dayOfWeek(),
                        range.startsAtTime(),
                        range.endsAtTime(),
                        range.validFrom(),
                        range.validUntil()))
            .toList(),
        view.exceptions().stream()
            .map(
                exception ->
                    new DateException(
                        exception.id(),
                        exception.exceptionDate(),
                        exception.startsAtTime(),
                        exception.endsAtTime(),
                        exception.kind()))
            .toList(),
        view.pauses().stream()
            .map(pause -> new Pause(pause.id(), pause.startsOn(), pause.endsOn()))
            .toList());
  }
}
