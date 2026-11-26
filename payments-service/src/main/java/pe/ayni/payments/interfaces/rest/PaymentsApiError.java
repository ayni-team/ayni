package pe.ayni.payments.interfaces.rest;

import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import org.springframework.http.HttpStatus;

/** The shape of every error, the same the monolith answers: the proxy passes it through as is. */
record PaymentsApiError(Instant timestamp, int status, String error, String message, String path) {

  static PaymentsApiError of(
      HttpStatus status, String message, HttpServletRequest request, Instant now) {
    return new PaymentsApiError(
        now, status.value(), status.getReasonPhrase(), message, request.getRequestURI());
  }
}
