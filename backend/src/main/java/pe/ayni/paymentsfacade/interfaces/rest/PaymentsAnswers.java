package pe.ayni.paymentsfacade.interfaces.rest;

import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import pe.ayni.paymentsfacade.interfaces.rest.PaymentsProxy.ServiceRefusal;

/** The two answers every route of this module may give that are not its own: shared by both advices. */
final class PaymentsAnswers {

  /**
   * What the student reads when the payments service is down or too slow (QA-06). Repeating a
   * purchase with the same Idempotency-Key never charges twice, which is why checking first is
   * enough.
   */
  static final String UNAVAILABLE =
      "Credit purchases are temporarily unavailable. The rest of Ayni works as usual. If you"
          + " started a purchase, check your purchases before trying again.";

  private PaymentsAnswers() {}

  /** The service's own error, its status and its body as they are. */
  static ResponseEntity<byte[]> passedOn(ServiceRefusal refusal) {
    return ResponseEntity.status(refusal.answer().status())
        .contentType(MediaType.APPLICATION_JSON)
        .body(refusal.answer().body());
  }

  static ResponseEntity<PaymentsApiError> unavailable(HttpServletRequest request, Instant now) {
    return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
        .header("Retry-After", "30")
        .body(PaymentsApiError.of(HttpStatus.SERVICE_UNAVAILABLE, UNAVAILABLE, request, now));
  }
}
