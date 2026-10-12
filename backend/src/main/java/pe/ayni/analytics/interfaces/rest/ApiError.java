package pe.ayni.analytics.interfaces.rest;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import org.springframework.http.HttpStatus;

/** The one shape every error of this API answers with. */
@Schema(name = "AnalyticsApiError", description = "What went wrong")
public record ApiError(
    @Schema(example = "2026-10-12T21:33:00Z") Instant timestamp,
    @Schema(example = "403") int status,
    @Schema(example = "Forbidden") String error,
    @Schema(example = "Only an active coordinator of the university can read its indicators")
        String message,
    @Schema(example = "/api/v1/coordinator/indicators") String path) {

  static ApiError of(HttpStatus status, String message, HttpServletRequest request, Instant now) {
    return new ApiError(
        now, status.value(), status.getReasonPhrase(), message, request.getRequestURI());
  }
}
