package pe.ayni.payments.interfaces.rest;

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
import pe.ayni.payments.domain.model.PurchaseIdempotencyConflict;
import pe.ayni.payments.domain.model.PurchaseLimitExceeded;
import pe.ayni.payments.domain.model.PurchaseRuleViolation;
import pe.ayni.shared.tenancy.MissingTenantException;
import pe.ayni.shared.tenancy.MissingUserException;

@RestControllerAdvice(assignableTypes = PaymentsController.class)
class PaymentsExceptionHandler {

  private final Clock clock;

  PaymentsExceptionHandler(Clock clock) {
    this.clock = clock;
  }

  @ExceptionHandler(PurchaseLimitExceeded.class)
  ResponseEntity<PaymentsApiError> handleLimit(
      PurchaseLimitExceeded exception, HttpServletRequest request) {
    return answer(HttpStatus.CONFLICT, exception.getMessage(), request);
  }

  @ExceptionHandler(PurchaseIdempotencyConflict.class)
  ResponseEntity<PaymentsApiError> handleIdempotencyConflict(
      PurchaseIdempotencyConflict exception, HttpServletRequest request) {
    return answer(HttpStatus.CONFLICT, exception.getMessage(), request);
  }

  @ExceptionHandler(PurchaseRuleViolation.class)
  ResponseEntity<PaymentsApiError> handleRuleViolation(
      PurchaseRuleViolation exception, HttpServletRequest request) {
    return answer(HttpStatus.BAD_REQUEST, exception.getMessage(), request);
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
