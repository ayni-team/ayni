package pe.ayni.sessions.interfaces.rest;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import org.springframework.http.HttpStatus;

/** The error shape returned by sessions endpoints. */
@Schema(name = "SessionsApiError", description = "What went wrong")
record ApiError(
    @Schema(example = "2026-09-29T21:00:00Z") Instant timestamp,
    @Schema(example = "409") int status,
    @Schema(example = "Conflict") String error,
    @Schema(example = "The room opens fifteen minutes before the session starts, at 2026-09-30T19:45:00Z")
        String message,
    @Schema(example = "/api/v1/sessions/5b1f0c2a-4d6e-4c7d-8e9f-0a1b2c3d4e5f/join") String path) {

  static ApiError of(HttpStatus status, String message, HttpServletRequest request, Instant now) {
    return new ApiError(
        now, status.value(), status.getReasonPhrase(), message, request.getRequestURI());
  }
}
