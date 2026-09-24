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
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import pe.ayni.booking.application.AddAvailabilityExceptionUseCase;
import pe.ayni.booking.application.AddAvailabilityPauseUseCase;
import pe.ayni.booking.application.DeclareWeeklyAvailabilityUseCase;
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

  AvailabilityController(
      DeclareWeeklyAvailabilityUseCase declareWeeklyAvailability,
      AddAvailabilityExceptionUseCase addAvailabilityException,
      AddAvailabilityPauseUseCase addAvailabilityPause) {
    this.declareWeeklyAvailability = declareWeeklyAvailability;
    this.addAvailabilityException = addAvailabilityException;
    this.addAvailabilityPause = addAvailabilityPause;
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
              + "A REMOVE without times covers the whole day.")
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
      description = "The availability exception was created",
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
              + "Overlapping pauses are rejected.")
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
      description = "The availability pause was created",
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

}
