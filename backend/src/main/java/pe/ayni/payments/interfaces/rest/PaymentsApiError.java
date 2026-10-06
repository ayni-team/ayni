package pe.ayni.payments.interfaces.rest;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import org.springframework.http.HttpStatus;

@Schema(name = "PaymentsApiError", description = "What went wrong")
record PaymentsApiError(
    @Schema(example = "2026-10-06T11:00:00Z") Instant timestamp,
    @Schema(example = "409") int status,
    @Schema(example = "Conflict") String error,
    @Schema(example = "Monthly purchase limit is 5 credits") String message,
    @Schema(example = "/api/v1/payments/purchases") String path) {

  static PaymentsApiError of(
      HttpStatus status, String message, HttpServletRequest request, Instant now) {
    return new PaymentsApiError(
        now, status.value(), status.getReasonPhrase(), message, request.getRequestURI());
  }
}
