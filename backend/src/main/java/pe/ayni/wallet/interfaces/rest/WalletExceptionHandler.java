package pe.ayni.wallet.interfaces.rest;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import java.time.Clock;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import pe.ayni.shared.tenancy.MissingTenantException;
import pe.ayni.wallet.InsufficientCreditsException;

/**
 * Turns the exceptions of this module into answers.
 *
 * <p>It is declared for wallet's controller alone, so each module keeps its own mapping: a refusal
 * that means one thing here should not quietly acquire a status code decided elsewhere.
 */
@RestControllerAdvice(assignableTypes = WalletController.class)
class WalletExceptionHandler {

  private final Clock clock;

  WalletExceptionHandler(Clock clock) {
    this.clock = clock;
  }

  /**
   * Not enough credits.
   *
   * <p>409 and not 400: the request was perfectly well formed, it is the state of the wallet that
   * refuses it. The message carries how many credits are missing.
   */
  @ExceptionHandler(InsufficientCreditsException.class)
  ResponseEntity<ApiError> handleInsufficientCredits(
      InsufficientCreditsException exception, HttpServletRequest request) {
    return answer(HttpStatus.CONFLICT, exception.getMessage(), request);
  }

  /** No university on the request: the header is missing. */
  @ExceptionHandler(MissingTenantException.class)
  ResponseEntity<ApiError> handleMissingTenant(
      MissingTenantException exception, HttpServletRequest request) {
    return answer(HttpStatus.BAD_REQUEST, exception.getMessage(), request);
  }

  /** A header the endpoint needs was not sent. */
  @ExceptionHandler(MissingRequestHeaderException.class)
  ResponseEntity<ApiError> handleMissingHeader(
      MissingRequestHeaderException exception, HttpServletRequest request) {
    return answer(
        HttpStatus.BAD_REQUEST,
        "The header " + exception.getHeaderName() + " is required",
        request);
  }

  /** A date, a user or a reason that could not be read. */
  @ExceptionHandler(MethodArgumentTypeMismatchException.class)
  ResponseEntity<ApiError> handleUnreadableParameter(
      MethodArgumentTypeMismatchException exception, HttpServletRequest request) {
    return answer(
        HttpStatus.BAD_REQUEST,
        "The value of " + exception.getName() + " could not be read",
        request);
  }

  /** A page or a size outside what the endpoint accepts. */
  @ExceptionHandler(ConstraintViolationException.class)
  ResponseEntity<ApiError> handleInvalidParameter(
      ConstraintViolationException exception, HttpServletRequest request) {
    return answer(HttpStatus.BAD_REQUEST, exception.getMessage(), request);
  }

  /** A rule of the domain refusing what was asked, such as a charge of zero credits. */
  @ExceptionHandler(IllegalArgumentException.class)
  ResponseEntity<ApiError> handleIllegalArgument(
      IllegalArgumentException exception, HttpServletRequest request) {
    return answer(HttpStatus.BAD_REQUEST, exception.getMessage(), request);
  }

  private ResponseEntity<ApiError> answer(
      HttpStatus status, String message, HttpServletRequest request) {
    return ResponseEntity.status(status)
        .body(ApiError.of(status, message, request, clock.instant()));
  }
}
