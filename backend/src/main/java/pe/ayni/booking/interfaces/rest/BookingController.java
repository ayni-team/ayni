package pe.ayni.booking.interfaces.rest;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import java.time.Instant;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import pe.ayni.booking.application.HoldHoursUseCase;
import pe.ayni.booking.application.ReleaseHoldsUseCase;
import pe.ayni.shared.tenancy.CurrentUser;

/**
 * Holding hours and booking them.
 *
 * <p>The controller only translates HTTP into use cases. The student is always read from {@link
 * CurrentUser}, never from the body: an endpoint with no way to name another student cannot hold or
 * book on their behalf.
 */
@RestController
@RequestMapping("/api/v1/bookings")
@Validated
@Tag(name = "Bookings", description = "Holding a tutor's hours and booking them")
class BookingController {

  private final HoldHoursUseCase holdHours;
  private final ReleaseHoldsUseCase releaseHolds;

  BookingController(HoldHoursUseCase holdHours, ReleaseHoldsUseCase releaseHolds) {
    this.holdHours = holdHours;
    this.releaseHolds = releaseHolds;
  }

  @PostMapping("/holds")
  @ResponseStatus(HttpStatus.CREATED)
  @Operation(
      summary = "Hold a tutor's hours for five minutes",
      description =
          "Takes one or more consecutive hours of a tutor out of circulation for five minutes, "
              + "so nobody else can take them while the student describes what they need. "
              + "Confirming the booking requires this hold. Holding hours the student already "
              + "holds does not extend the hold.")
  @Parameter(
      in = ParameterIn.HEADER,
      name = "X-Tenant-Id",
      required = true,
      description = "University the request belongs to. Read by TenantFilter",
      schema = @Schema(type = "string", example = "UPC"))
  @Parameter(
      in = ParameterIn.HEADER,
      name = "X-User-Id",
      required = true,
      description = "Student holding the hours. Read by CurrentUserFilter",
      schema = @Schema(type = "string", format = "uuid",
          example = "11111111-1111-4111-8111-111111111111"))
  @ApiResponse(
      responseCode = "201",
      description = "The hours are held for the student",
      content = @Content(schema = @Schema(implementation = HeldHoursResponse.class)))
  @ApiResponse(
      responseCode = "400",
      description = "The request is invalid, or the student is the tutor",
      content = @Content(schema = @Schema(implementation = ApiError.class)))
  @ApiResponse(
      responseCode = "403",
      description = "The student's account is not active",
      content = @Content(schema = @Schema(implementation = ApiError.class)))
  @ApiResponse(
      responseCode = "409",
      description =
          "The tutor does not offer those consecutive hours, or one of them has started, is "
              + "booked, or another student holds it",
      content = @Content(schema = @Schema(implementation = ApiError.class)))
  HeldHoursResponse hold(@Valid @RequestBody HoldHoursRequest request) {
    UUID studentId = CurrentUser.require();
    return HeldHoursResponse.of(
        holdHours.execute(studentId, request.tutorId(), request.start(), request.hours()));
  }

  @DeleteMapping("/holds")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @Operation(
      summary = "Give back held hours",
      description =
          "Releases the hours the student holds in that stretch, when they leave the "
              + "confirmation, so the hours return to the search at once. Hours the student does "
              + "not hold are left alone, so repeating the call is harmless.")
  @Parameter(
      in = ParameterIn.HEADER,
      name = "X-Tenant-Id",
      required = true,
      description = "University the request belongs to. Read by TenantFilter",
      schema = @Schema(type = "string", example = "UPC"))
  @Parameter(
      in = ParameterIn.HEADER,
      name = "X-User-Id",
      required = true,
      description = "Student giving the hours back. Read by CurrentUserFilter",
      schema = @Schema(type = "string", format = "uuid",
          example = "11111111-1111-4111-8111-111111111111"))
  @ApiResponse(responseCode = "204", description = "Whatever the student held there is free again")
  @ApiResponse(
      responseCode = "400",
      description = "A parameter is missing or invalid",
      content = @Content(schema = @Schema(implementation = ApiError.class)))
  void release(
      @Parameter(description = "Tutor whose hours were held",
              example = "22222222-2222-4222-8222-222222222222")
          @RequestParam
          UUID tutorId,
      @Parameter(description = "Start of the first hour, ISO 8601 UTC",
              example = "2026-09-29T23:00:00Z")
          @RequestParam
          Instant start,
      @Parameter(description = "How many consecutive hours from the start", example = "2")
          @RequestParam
          @Min(1)
          int hours) {
    releaseHolds.execute(CurrentUser.require(), tutorId, start, hours);
  }
}
