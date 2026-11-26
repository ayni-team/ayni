package pe.ayni.paymentsfacade.interfaces.rest;

import jakarta.servlet.http.HttpServletRequest;
import java.time.Clock;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import pe.ayni.paymentsfacade.infrastructure.PaymentsServiceUnavailable;
import pe.ayni.paymentsfacade.interfaces.rest.PaymentsProxy.ServiceRefusal;

/** The errors of the switch of the simulated payment provider, with the shape of every error. */
@Profile("demo")
@RestControllerAdvice(assignableTypes = DemoPaymentsController.class)
class DemoPaymentsExceptionHandler {

  private final Clock clock;

  DemoPaymentsExceptionHandler(Clock clock) {
    this.clock = clock;
  }

  /** An outcome that does not exist, for instance: the service says which, in its own words. */
  @ExceptionHandler(ServiceRefusal.class)
  ResponseEntity<byte[]> serviceRefusal(ServiceRefusal refusal) {
    return PaymentsAnswers.passedOn(refusal);
  }

  @ExceptionHandler(PaymentsServiceUnavailable.class)
  ResponseEntity<PaymentsApiError> unavailable(
      PaymentsServiceUnavailable exception, HttpServletRequest request) {
    return PaymentsAnswers.unavailable(request, clock.instant());
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
