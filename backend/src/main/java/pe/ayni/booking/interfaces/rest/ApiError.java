package pe.ayni.booking.interfaces.rest;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import org.springframework.http.HttpStatus;

/** The error shape returned by booking endpoints. */
@Schema(name = "BookingApiError", description = "What went wrong")
record ApiError(
    @Schema(example = "2026-09-23T21:00:00Z") Instant timestamp,
    @Schema(example = "400") int status,
    @Schema(example = "Bad Request") String error,
    @Schema(example = "The pause overlaps an existing availability pause") String message,
    @Schema(example = "/api/v1/tutor/availability/pauses") String path) {

  static ApiError of(
      HttpStatus status, String message, HttpServletRequest request, Instant now) {
    return new ApiError(
        now, status.value(), status.getReasonPhrase(), message, request.getRequestURI());
  }
}
