package pe.ayni.notifications.interfaces.rest;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import org.springframework.http.HttpStatus;

/** The error shape returned by notifications endpoints. */
@Schema(name = "NotificationsApiError", description = "What went wrong")
record ApiError(
    @Schema(example = "2026-10-20T14:31:05Z") Instant timestamp,
    @Schema(example = "404") int status,
    @Schema(example = "Not Found") String error,
    @Schema(example = "Notification not found: 5b1f0c2a-4d6e-4c7d-8e9f-0a1b2c3d4e5f")
        String message,
    @Schema(example = "/api/v1/notifications/5b1f0c2a-4d6e-4c7d-8e9f-0a1b2c3d4e5f/read")
        String path) {

  static ApiError of(HttpStatus status, String message, HttpServletRequest request, Instant now) {
    return new ApiError(
        now, status.value(), status.getReasonPhrase(), message, request.getRequestURI());
  }
}
