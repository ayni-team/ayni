package pe.ayni.paymentsfacade.interfaces.rest;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import java.time.Clock;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import pe.ayni.paymentsfacade.infrastructure.PaymentsServiceUnavailable;
import pe.ayni.paymentsfacade.interfaces.rest.PaymentsProxy.ServiceRefusal;
import pe.ayni.shared.tenancy.MissingTenantException;
import pe.ayni.shared.tenancy.MissingUserException;

/**
 * The errors of the purchase routes. What the service decides comes back from it; what is checked
 * before forwarding (the body, the headers, the session) is answered here with the same words the
 * payments module used, so the answers did not change when the module left.
 */
@RestControllerAdvice(assignableTypes = PaymentsController.class)
class PaymentsExceptionHandler {

  private final Clock clock;

  PaymentsExceptionHandler(Clock clock) {
    this.clock = clock;
  }

  /** The limit, an idempotency conflict, a purchase not found: the service's words, unchanged. */
  @ExceptionHandler(ServiceRefusal.class)
  ResponseEntity<byte[]> handleServiceRefusal(ServiceRefusal refusal) {
    return PaymentsAnswers.passedOn(refusal);
  }

  @ExceptionHandler(PaymentsServiceUnavailable.class)
  ResponseEntity<PaymentsApiError> handleUnavailable(
      PaymentsServiceUnavailable exception, HttpServletRequest request) {
    return PaymentsAnswers.unavailable(request, clock.instant());
  }

  @ExceptionHandler({MissingTenantException.class, MissingUserException.class})
  ResponseEntity<PaymentsApiError> handleMissingContext(
      RuntimeException exception, HttpServletRequest request) {
    return answer(HttpStatus.BAD_REQUEST, exception.getMessage(), request);
  }

  @ExceptionHandler(MissingRequestHeaderException.class)
  ResponseEntity<PaymentsApiError> handleMissingHeader(
      MissingRequestHeaderException exception, HttpServletRequest request) {
    return answer(
        HttpStatus.BAD_REQUEST, "The header " + exception.getHeaderName() + " is required", request);
  }

  @ExceptionHandler({
    ConstraintViolationException.class,
    HandlerMethodValidationException.class,
    MethodArgumentNotValidException.class,
    HttpMessageNotReadableException.class,
    MethodArgumentTypeMismatchException.class
  })
  ResponseEntity<PaymentsApiError> handleInvalidRequest(
      Exception exception, HttpServletRequest request) {
    return answer(HttpStatus.BAD_REQUEST, "The purchase request is invalid", request);
  }

  private ResponseEntity<PaymentsApiError> answer(
      HttpStatus status, String message, HttpServletRequest request) {
    return ResponseEntity.status(status)
        .body(PaymentsApiError.of(status, message, request, clock.instant()));
  }
}
