package pe.ayni.skills.interfaces.rest;

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
@Schema(name = "SkillsApiError", description = "What went wrong")
public record ApiError(
    @Schema(example = "2026-09-22T19:08:39Z") Instant timestamp,
    @Schema(example = "400") int status,
    @Schema(example = "Bad Request") String error,
    @Schema(example = "grade 12.00 does not reach the university's threshold 13.00") String message,
    @Schema(example = "/api/v1/skills/offers") String path) {

  static ApiError of(HttpStatus status, String message, HttpServletRequest request, Instant now) {
    return new ApiError(
        now, status.value(), status.getReasonPhrase(), message, request.getRequestURI());
  }
}
