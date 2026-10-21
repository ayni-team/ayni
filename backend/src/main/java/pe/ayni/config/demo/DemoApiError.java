package pe.ayni.config.demo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import org.springframework.http.HttpStatus;

/** The shape of every error, as in the modules, for the endpoints of the demonstration. */
@Schema(name = "DemoApiError", description = "What went wrong")
public record DemoApiError(
    @Schema(example = "2026-10-21T15:05:00Z") Instant timestamp,
    @Schema(example = "400") int status,
    @Schema(example = "Bad Request") String error,
    @Schema(example = "by must be an ISO 8601 duration, such as PT2H or -P1D") String message,
    @Schema(example = "/api/v1/admin/demo/clock/advance") String path) {

  static DemoApiError of(
      HttpStatus status, String message, HttpServletRequest request, Instant now) {
    return new DemoApiError(
        now, status.value(), status.getReasonPhrase(), message, request.getRequestURI());
  }
}
