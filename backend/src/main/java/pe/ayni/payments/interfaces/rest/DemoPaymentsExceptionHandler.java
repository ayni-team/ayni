package pe.ayni.payments.interfaces.rest;

import jakarta.servlet.http.HttpServletRequest;
import java.time.Clock;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import pe.ayni.payments.application.InvalidSimulatedOutcome;

/** The errors of the switch of the simulated payment provider, with the shape of every error. */
@Profile("demo")
@RestControllerAdvice(assignableTypes = DemoPaymentsController.class)
class DemoPaymentsExceptionHandler {

  private final Clock clock;

  DemoPaymentsExceptionHandler(Clock clock) {
    this.clock = clock;
  }

  @ExceptionHandler(InvalidSimulatedOutcome.class)
  ResponseEntity<PaymentsApiError> invalidOutcome(
      InvalidSimulatedOutcome exception, HttpServletRequest request) {
    return answer(exception.getMessage(), request);
  }

  @ExceptionHandler(HttpMessageNotReadableException.class)
  ResponseEntity<PaymentsApiError> unreadableBody(
      HttpMessageNotReadableException exception, HttpServletRequest request) {
    return answer("The body is missing or is not the JSON this endpoint takes", request);
  }

  private ResponseEntity<PaymentsApiError> answer(String message, HttpServletRequest request) {
    return ResponseEntity.badRequest()
        .body(PaymentsApiError.of(HttpStatus.BAD_REQUEST, message, request, clock.instant()));
  }
}
