package pe.ayni.wallet.interfaces.rest;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import org.springframework.http.HttpStatus;

/**
 * The one shape every error of this API answers with.
 *
 * <p>Same five fields for every failure, so the web application has one thing to handle and not one
 * per endpoint. A refusal the screen has to tell apart from another conflict adds a stable {@code
 * code}, and a refusal for lack of credits also says how many are missing in {@code
 * missingCredits}. Both are left out of every other answer.
 */
@Schema(name = "WalletApiError", description = "What went wrong")
public record ApiError(
    @Schema(example = "2026-09-20T14:31:05Z") Instant timestamp,
    @Schema(example = "409") int status,
    @Schema(example = "Conflict") String error,
    @Schema(example = "Insufficient credits, missing 2") String message,
    @Schema(example = "/api/v1/wallet") String path,
    @JsonInclude(JsonInclude.Include.NON_NULL)
        @Schema(
            description =
                "Stable name of a refusal the screen tells apart: INSUFFICIENT_CREDITS,"
                    + " INSUFFICIENT_EARNED_CREDITS, CONFIRMATION_REQUIRED, CONFIRMATION_INVALID,"
                    + " CONFIRMATION_EXPIRED, CONFIRMATION_MISMATCH, CONFIRMATION_USED or"
                    + " IDEMPOTENCY_CONFLICT. Absent on other errors",
            example = "INSUFFICIENT_EARNED_CREDITS",
            nullable = true)
        String code,
    @JsonInclude(JsonInclude.Include.NON_NULL)
        @Schema(
            description =
                "How many credits the operation lacks. Present only with INSUFFICIENT_CREDITS and"
                    + " INSUFFICIENT_EARNED_CREDITS",
            example = "2",
            nullable = true)
        Integer missingCredits) {

  static ApiError of(HttpStatus status, String message, HttpServletRequest request, Instant now) {
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
