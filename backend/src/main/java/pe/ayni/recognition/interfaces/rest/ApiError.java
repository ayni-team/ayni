package pe.ayni.recognition.interfaces.rest;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import org.springframework.http.HttpStatus;

/**
 * The one shape every error of this API answers with.
 *
 * <p>Same five fields for every failure, so the web application has one thing to handle and not one
 * per endpoint.
 */
@Schema(name = "RecognitionApiError", description = "What went wrong")
public record ApiError(
    @Schema(example = "2026-10-05T19:08:39Z") Instant timestamp,
    @Schema(example = "409") int status,
    @Schema(example = "Conflict") String error,
    @Schema(example = "4 more hours are needed before recognition can be requested") String message,
    @Schema(example = "/api/v1/recognition/requests") String path) {

  static ApiError of(HttpStatus status, String message, HttpServletRequest request, Instant now) {
    return new ApiError(
        now, status.value(), status.getReasonPhrase(), message, request.getRequestURI());
  }
}
