package pe.ayni.booking.interfaces.rest;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import pe.ayni.booking.application.TutorAgendaQuery;
import pe.ayni.shared.tenancy.CurrentUser;

/**
 * The tutor's agenda (US21). The tutor is always the person who asks, read from {@link
 * CurrentUser}: there is no way to name another tutor.
 */
@RestController
@RequestMapping("/api/v1/tutor/agenda")
@Validated
@Tag(name = "Tutor agenda", description = "What a tutor has booked and still free, and what they already taught")
class TutorAgendaController {

  private final TutorAgendaQuery agenda;

  TutorAgendaController(TutorAgendaQuery agenda) {
    this.agenda = agenda;
  }

  @GetMapping
  @Operation(
      summary = "What is booked and what is still free, day by day",
      description =
          """
          The sessions booked with the signed in tutor and the hours of theirs nobody booked yet,
          in the order they come, from the first day to the last, both included. The days are
          those of the university's time zone, and every line carries its UTC instants and the
          same day and times in that zone. A booked session appears once with its student, its
          course or skill and the need the student described, and joinable is true from fifteen
          minutes before the start until the end. A free hour that has started is no longer
          shown, since it cannot be booked. Without from, the agenda starts today; without to, it
          covers a week.
          """)
  @ApiResponse(
      responseCode = "200",
      description = "The agenda of the period, possibly empty",
      content = @Content(schema = @Schema(implementation = TutorAgendaResponse.class)))
  @ApiResponse(
      responseCode = "400",
      description =
          "A date is not ISO 8601, to is before from, or the period is longer than 31 days",
      content = @Content(schema = @Schema(implementation = ApiError.class)))
  TutorAgendaResponse agenda(
      @Parameter(description = "First day, ISO 8601, in the university's zone. Default: today",
              example = "2026-10-12")
          @RequestParam(required = false)
          @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
          LocalDate from,
      @Parameter(description = "Last day, included. Default: six days after from",
              example = "2026-10-18")
          @RequestParam(required = false)
          @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
          LocalDate to) {
    return TutorAgendaResponse.of(agenda.agenda(CurrentUser.require(), from, to));
  }

  @GetMapping("/history")
  @Operation(
      summary = "The sessions the tutor already had",
      description =
          """
          The bookings of the signed in tutor that are over, were cancelled or were not attended,
          the most recent first, one page at a time, apart from the agenda ahead. Each carries the
          student, the course or skill, the need, the UTC instants and its status.
          """)
  @ApiResponse(
      responseCode = "200",
      description = "A page of past sessions, possibly empty",
      content = @Content(schema = @Schema(implementation = TutorBookingsResponse.class)))
  @ApiResponse(
      responseCode = "400",
      description = "page or size is out of range",
      content = @Content(schema = @Schema(implementation = ApiError.class)))
  TutorBookingsResponse history(
      @Parameter(description = "Page number, from 0", example = "0")
          @RequestParam(defaultValue = "0")
          @Min(0)
          int page,
      @Parameter(description = "Page size, 1 to 100", example = "20")
          @RequestParam(defaultValue = "20")
          @Min(1)
          @Max(100)
          int size) {
    return TutorBookingsResponse.of(agenda.history(CurrentUser.require(), page, size));
  }
}
