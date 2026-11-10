package pe.ayni.booking.interfaces.rest;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import org.springframework.http.HttpStatus;

/**
 * The error shape returned by booking endpoints.
 *
 * <p>A refusal for lack of credits also carries {@code code} and {@code missingCredits}, so the
 * screen tells it apart from the other conflicts of a booking without reading the message. Both are
 * left out of every other answer.
 */
@Schema(name = "BookingApiError", description = "What went wrong")
record ApiError(
    @Schema(example = "2026-09-23T21:00:00Z") Instant timestamp,
    @Schema(example = "400") int status,
    @Schema(example = "Bad Request") String error,
    @Schema(example = "The pause overlaps an existing availability pause") String message,
    @Schema(example = "/api/v1/tutor/availability/pauses") String path,
    @JsonInclude(JsonInclude.Include.NON_NULL)
        @Schema(
            description =
                "Stable name of the refusal: INSUFFICIENT_CREDITS when the balance does not cover"
                    + " the hours. Absent on other errors",
            example = "INSUFFICIENT_CREDITS",
            nullable = true)
        String code,
    @JsonInclude(JsonInclude.Include.NON_NULL)
        @Schema(
            description = "How many credits the booking lacks. Present only with INSUFFICIENT_CREDITS",
            example = "2",
            nullable = true)
        Integer missingCredits) {

  static ApiError of(
      HttpStatus status, String message, HttpServletRequest request, Instant now) {
    return of(status, message, null, null, request, now);
  }

  static ApiError of(
      HttpStatus status,
      String message,
      String code,
      Integer missingCredits,
      HttpServletRequest request,
      Instant now) {
    return new ApiError(
        now,
        status.value(),
        status.getReasonPhrase(),
        message,
        request.getRequestURI(),
        code,
        missingCredits);
  }
}
