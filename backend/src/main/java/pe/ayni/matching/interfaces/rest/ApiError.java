package pe.ayni.matching.interfaces.rest;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import org.springframework.http.HttpStatus;

/** The error shape returned by matching endpoints. */
@Schema(name = "MatchingApiError", description = "What went wrong")
record ApiError(
    @Schema(example = "2026-09-29T21:00:00Z") Instant timestamp,
    @Schema(example = "400") int status,
    @Schema(example = "Bad Request") String error,
    @Schema(example = "from must be before to") String message,
    @Schema(example = "/api/v1/search/offers") String path) {

  static ApiError of(
      HttpStatus status, String message, HttpServletRequest request, Instant now) {
    return new ApiError(
        now, status.value(), status.getReasonPhrase(), message, request.getRequestURI());
  }
}
