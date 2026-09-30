package pe.ayni.booking.interfaces.rest;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import pe.ayni.booking.application.BookHoursUseCase;
import pe.ayni.booking.application.CancelBookingUseCase;
import pe.ayni.booking.application.HoldHoursUseCase;
import pe.ayni.booking.application.LateCancellationConfirmation;
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
  private final BookHoursUseCase bookHours;
  private final CancelBookingUseCase cancelBooking;

  BookingController(
      HoldHoursUseCase holdHours,
      ReleaseHoldsUseCase releaseHolds,
      BookHoursUseCase bookHours,
      CancelBookingUseCase cancelBooking) {
    this.holdHours = holdHours;
    this.releaseHolds = releaseHolds;
    this.bookHours = bookHours;
    this.cancelBooking = cancelBooking;
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  @Operation(
      summary = "Book the hours the student is holding",
      description =
          "Confirms a booking of consecutive hours the student holds, in one transaction: the "
              + "tutor is enabled for the subject, the hours are consecutive and held by the "
              + "student, one credit per hour is charged (credits closest to expiring first), "
              + "the hours are marked as booked, the booking is saved and BookingConfirmed is "
              + "published. If anything fails nothing is charged and the student's holds on "
              + "those hours are released.")
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
      description = "Student booking. Read by CurrentUserFilter",
      schema = @Schema(type = "string", format = "uuid",
          example = "11111111-1111-4111-8111-111111111111"))
  @ApiResponse(
      responseCode = "201",
      description = "The booking is confirmed and the credits were charged",
      content = @Content(schema = @Schema(implementation = BookingResponse.class)))
  @ApiResponse(
      responseCode = "400",
      description = "The request is invalid, the need is not described, or the student is the tutor",
      content = @Content(schema = @Schema(implementation = ApiError.class)))
  @ApiResponse(
      responseCode = "403",
      description = "The student's account is not active",
      content = @Content(schema = @Schema(implementation = ApiError.class)))
  @ApiResponse(
      responseCode = "404",
      description = "The subject does not exist for the student's university",
      content = @Content(schema = @Schema(implementation = ApiError.class)))
  @ApiResponse(
      responseCode = "409",
      description =
          "Nothing was charged: the tutor is not enabled for the subject, the hours are not "
              + "offered, the hold ran out, another student took the hour, or the balance does "
              + "not cover it (the message says how many credits are missing)",
      content =
          @Content(
              schema = @Schema(implementation = ApiError.class),
              examples =
                  @ExampleObject(
                      name = "Insufficient credits",
                      value =
                          """
                          {"timestamp":"2026-09-24T02:01:00Z","status":409,"error":"Conflict",
                           "message":"Not enough credits: 2 more are needed to book these hours",
                           "path":"/api/v1/bookings"}
                          """)))
  @ApiResponse(
      responseCode = "500",
      description = "The booking failed unexpectedly. Nothing was charged",
      content = @Content(schema = @Schema(implementation = ApiError.class)))
  BookingResponse book(@Valid @RequestBody BookHoursRequest request) {
    UUID studentId = CurrentUser.require();
    return BookingResponse.of(
        bookHours.execute(
            studentId,
            request.tutorId(),
            request.catalogItemId(),
            request.start(),
            request.hours(),
            request.needDescription()));
  }

  @DeleteMapping("/{id}")
  @Operation(
      summary = "Cancel a confirmed tutoring booking",
      description =
          """
          The student or tutor who is part of the booking may cancel it before the session starts.
          A cancellation at least twelve hours before the start returns the credits to their
          original lots and makes the student's hours available again. Inside twelve hours the
          first request returns a warning without cancelling; repeat it with confirmLate=true to
          proceed without a refund. A started session cannot be cancelled.
          """)
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
      description = "Student or tutor cancelling the booking. Read by CurrentUserFilter",
      schema =
          @Schema(
              type = "string",
              format = "uuid",
              example = "11111111-1111-4111-8111-111111111111"))
  @ApiResponse(responseCode = "204", description = "The booking was cancelled")
  @ApiResponse(
      responseCode = "409",
      description =
          "Late cancellation requires explicit confirmation, or the booking has already started",
      content =
          @Content(
              schema = @Schema(implementation = LateCancellationResponse.class),
              examples =
                  @ExampleObject(
                      name = "Late cancellation confirmation required",
                      value =
                          """
                          {"bookingId":"d1f0c2a4-5b6e-4c7d-8e9f-0a1b2c3d4e5f",
                           "startsAt":"2026-09-29T23:00:00Z","late":true,
                           "refundWillBeIssued":false,"confirmationRequired":true,
                           "confirmationRequest":"DELETE /api/v1/bookings/d1f0c2a4-5b6e-4c7d-8e9f-0a1b2c3d4e5f?confirmLate=true"}
                          """)))
  @ApiResponse(
      responseCode = "404",
      description = "The booking does not exist or the caller is not a participant",
      content = @Content(schema = @Schema(implementation = ApiError.class)))
  @ApiResponse(
      responseCode = "400",
      description = "The request is missing the required tenant or user context",
      content = @Content(schema = @Schema(implementation = ApiError.class)))
  ResponseEntity<?> cancel(
      @PathVariable UUID id,
      @Parameter(
              description =
                  "Set to true only after the caller accepts that a late cancellation receives no refund.",
              example = "true")
          @RequestParam(defaultValue = "false")
          boolean confirmLate) {
    Optional<LateCancellationConfirmation> confirmation =
        cancelBooking.execute(CurrentUser.require(), id, confirmLate);
    if (confirmation.isPresent()) {
      return ResponseEntity.status(HttpStatus.CONFLICT)
          .body(LateCancellationResponse.of(confirmation.orElseThrow()));
    }
    return ResponseEntity.noContent().build();
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
