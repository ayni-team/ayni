package pe.ayni.booking.interfaces.rest;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import pe.ayni.booking.application.AddAvailabilityExceptionUseCase;
import pe.ayni.booking.application.AddAvailabilityPauseUseCase;
import pe.ayni.booking.application.DeclareWeeklyAvailabilityUseCase;
import pe.ayni.booking.application.GetCurrentAvailabilityPauseUseCase;
import pe.ayni.booking.application.ReactivateAvailabilityPauseUseCase;
import pe.ayni.booking.application.RemoveAvailabilityPatternUseCase;
import pe.ayni.shared.tenancy.CurrentUser;

/**
 * Tutor availability commands.
 *
 * <p>The controller only translates HTTP requests into application commands. The tutor is always
 * read from {@link CurrentUser}; it is never accepted in the request body.
 */
@RestController
@RequestMapping("/api/v1")
@Tag(name = "Availability", description = "Tutor weekly availability, exceptions and pauses")
class AvailabilityController {

  private final DeclareWeeklyAvailabilityUseCase declareWeeklyAvailability;
  private final AddAvailabilityExceptionUseCase addAvailabilityException;
  private final AddAvailabilityPauseUseCase addAvailabilityPause;
  private final GetCurrentAvailabilityPauseUseCase getCurrentAvailabilityPause;
  private final ReactivateAvailabilityPauseUseCase reactivateAvailabilityPause;
  private final RemoveAvailabilityPatternUseCase removeAvailabilityPattern;

  AvailabilityController(
      DeclareWeeklyAvailabilityUseCase declareWeeklyAvailability,
      AddAvailabilityExceptionUseCase addAvailabilityException,
      AddAvailabilityPauseUseCase addAvailabilityPause,
      GetCurrentAvailabilityPauseUseCase getCurrentAvailabilityPause,
      ReactivateAvailabilityPauseUseCase reactivateAvailabilityPause,
      RemoveAvailabilityPatternUseCase removeAvailabilityPattern) {
    this.declareWeeklyAvailability = declareWeeklyAvailability;
    this.addAvailabilityException = addAvailabilityException;
    this.addAvailabilityPause = addAvailabilityPause;
    this.getCurrentAvailabilityPause = getCurrentAvailabilityPause;
    this.reactivateAvailabilityPause = reactivateAvailabilityPause;
    this.removeAvailabilityPattern = removeAvailabilityPattern;
  }

  @PostMapping("/tutor/availability")
  @ResponseStatus(HttpStatus.CREATED)
  @Operation(
      summary = "Declare weekly tutor availability",
      description =
          "Creates a recurring availability range for the tutor making the request and "
              + "generates its one-hour blocks for the coming weeks, in the university's time "
              + "zone. Overlapping ranges are rejected, while adjacent ranges are allowed. A tutor "
              + "without an enabled skill keeps the range but gets no blocks, and the answer "
              + "carries a notice saying why.")
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
      description = "Tutor making the request. Read by CurrentUserFilter",
      schema = @Schema(type = "string", format = "uuid",
          example = "11111111-1111-4111-8111-111111111111"))
  @ApiResponse(
      responseCode = "201",
      description = "The weekly availability was created",
      content = @Content(schema = @Schema(implementation = AvailabilityPatternResponse.class)))
  @ApiResponse(
      responseCode = "400",
      description = "The request is invalid or the range overlaps an existing pattern",
      content = @Content(schema = @Schema(implementation = ApiError.class)))
  AvailabilityPatternResponse declareWeeklyAvailability(
      @Valid @RequestBody DeclareAvailabilityRequest request) {
    UUID tutorId = CurrentUser.require();
    return AvailabilityPatternResponse.of(
        declareWeeklyAvailability.execute(
            tutorId,
            request.dayOfWeek(),
            request.startsAtTime(),
            request.endsAtTime(),
            request.validFrom(),
            request.validUntil()));
  }

  @PostMapping("/tutor/availability/exceptions")
  @ResponseStatus(HttpStatus.CREATED)
  @Operation(
      summary = "Add a date-specific availability exception",
      description =
          "Adds extraordinary availability or removes availability for one date. "
              + "A REMOVE without times covers the whole day. The hours of that date follow at "
              + "once: a REMOVE withdraws the free or held hours it covers, which leave the "
              + "search, and an ADD generates the hours it gives. Booked hours stand: the answer "
              + "counts them and says so.")
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
      description = "Tutor making the request. Read by CurrentUserFilter",
      schema = @Schema(type = "string", format = "uuid",
          example = "11111111-1111-4111-8111-111111111111"))
  @ApiResponse(
      responseCode = "201",
      description = "The exception was created and the hours of that date adjusted",
      content = @Content(schema = @Schema(implementation = AvailabilityExceptionResponse.class)))
  @ApiResponse(
      responseCode = "400",
      description = "The request is invalid",
      content = @Content(schema = @Schema(implementation = ApiError.class)))
  AvailabilityExceptionResponse addException(
      @Valid @RequestBody AvailabilityExceptionRequest request) {
    UUID tutorId = CurrentUser.require();
    return AvailabilityExceptionResponse.of(
        addAvailabilityException.execute(
            tutorId,
            request.exceptionDate(),
            request.startsAtTime(),
            request.endsAtTime(),
            request.kind()));
  }

  @PostMapping("/tutor/availability/pauses")
  @ResponseStatus(HttpStatus.CREATED)
  @Operation(
      summary = "Pause tutor availability",
      description =
          "Pauses all availability for the tutor during the inclusive date range. "
              + "Overlapping pauses are rejected. The hours of those days that already exist, "
              + "free or held, are withdrawn at once and leave the search. Booked hours stand: "
              + "the answer counts them and says so.")
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
      description = "Tutor making the request. Read by CurrentUserFilter",
      schema = @Schema(type = "string", format = "uuid",
          example = "11111111-1111-4111-8111-111111111111"))
  @ApiResponse(
      responseCode = "201",
      description = "The pause was created and its hours withdrawn",
      content = @Content(schema = @Schema(implementation = AvailabilityPauseResponse.class)))
  @ApiResponse(
      responseCode = "400",
      description = "The request is invalid or the pause overlaps an existing pause",
      content = @Content(schema = @Schema(implementation = ApiError.class)))
  AvailabilityPauseResponse addPause(@Valid @RequestBody AvailabilityPauseRequest request) {
    UUID tutorId = CurrentUser.require();
    return AvailabilityPauseResponse.of(
        addAvailabilityPause.execute(tutorId, request.startsOn(), request.endsOn()));
  }

  @GetMapping("/tutor/availability/pauses/current")
  @Operation(
      summary = "Read whether the tutor is currently paused",
      description =
          "Returns whether today's local date is inside a pause and, when paused, the pause "
              + "identifier and its inclusive end date.")
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
      description = "Tutor making the request. Read by CurrentUserFilter",
      schema =
          @Schema(
              type = "string",
              format = "uuid",
              example = "11111111-1111-4111-8111-111111111111"))
  @ApiResponse(
      responseCode = "200",
      description = "Current pause state",
      content = @Content(schema = @Schema(implementation = CurrentAvailabilityPauseResponse.class)))
  CurrentAvailabilityPauseResponse currentPause() {
    return CurrentAvailabilityPauseResponse.of(
        getCurrentAvailabilityPause.execute(CurrentUser.require()));
  }

  @DeleteMapping("/tutor/availability/pauses/{id}")
  @Operation(
      summary = "Reactivate tutor availability before the pause ends",
      description =
          "Ends the current pause and restores the future hours still provided by the tutor's "
              + "weekly availability. Confirmed bookings are unchanged.")
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
      description = "Tutor making the request. Read by CurrentUserFilter",
      schema =
          @Schema(
              type = "string",
              format = "uuid",
              example = "11111111-1111-4111-8111-111111111111"))
  @Parameter(
      in = ParameterIn.PATH,
      name = "id",
      required = true,
      description = "Identifier of the tutor's active pause",
      schema = @Schema(type = "string", format = "uuid"))
  @ApiResponse(
      responseCode = "200",
      description = "The pause ended and eligible hours were restored",
      content =
          @Content(schema = @Schema(implementation = ReactivatedAvailabilityPauseResponse.class)))
  @ApiResponse(
      responseCode = "404",
      description = "The pause is not active or does not belong to this tutor",
      content = @Content(schema = @Schema(implementation = ApiError.class)))
  ReactivatedAvailabilityPauseResponse reactivatePause(@PathVariable("id") UUID pauseId) {
    return ReactivatedAvailabilityPauseResponse.of(
        reactivateAvailabilityPause.execute(CurrentUser.require(), pauseId));
  }

  @DeleteMapping("/tutor/availability/{id}")
  @Operation(
      summary = "Remove a weekly availability range",
      description =
          "Removes the tutor's recurring range and withdraws its future free or held hours. "
              + "Confirmed bookings remain unchanged.")
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
      description = "Tutor making the request. Read by CurrentUserFilter",
      schema =
          @Schema(
              type = "string",
              format = "uuid",
              example = "11111111-1111-4111-8111-111111111111"))
  @Parameter(
      in = ParameterIn.PATH,
      name = "id",
      required = true,
      description = "Identifier of the tutor's weekly availability range",
      schema = @Schema(type = "string", format = "uuid"))
  @ApiResponse(
      responseCode = "200",
      description = "The weekly range was removed and its hours adjusted",
      content =
          @Content(schema = @Schema(implementation = RemovedAvailabilityPatternResponse.class)))
  @ApiResponse(
      responseCode = "404",
      description = "The range does not belong to this tutor",
      content = @Content(schema = @Schema(implementation = ApiError.class)))
  RemovedAvailabilityPatternResponse removeWeeklyAvailability(
      @PathVariable("id") UUID patternId) {
    return RemovedAvailabilityPatternResponse.of(
        removeAvailabilityPattern.execute(CurrentUser.require(), patternId));
  }

}
