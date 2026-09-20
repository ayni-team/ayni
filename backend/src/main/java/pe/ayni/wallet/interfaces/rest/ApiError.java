package pe.ayni.wallet.interfaces.rest;

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
@Schema(name = "ApiError", description = "What went wrong")
public record ApiError(
    @Schema(example = "2026-09-20T14:31:05Z") Instant timestamp,
    @Schema(example = "409") int status,
    @Schema(example = "Conflict") String error,
    @Schema(example = "Insufficient credits, missing 2") String message,
    @Schema(example = "/api/v1/wallet") String path) {

  static ApiError of(HttpStatus status, String message, HttpServletRequest request, Instant now) {
    return new ApiError(
        now, status.value(), status.getReasonPhrase(), message, request.getRequestURI());
  }
}
