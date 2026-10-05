package pe.ayni.recognition.interfaces.rest;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import java.time.Clock;
import java.util.NoSuchElementException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import pe.ayni.recognition.domain.model.InsufficientHours;
import pe.ayni.recognition.domain.model.RecognitionStateConflict;
import pe.ayni.shared.tenancy.MissingTenantException;
import pe.ayni.shared.tenancy.MissingUserException;

/**
 * Turns the exceptions of this module into answers.
 *
 * <p>It is declared for the controllers of this module alone, so each module keeps its own mapping: a
 * refusal that means one thing here should not quietly acquire a status code decided elsewhere.
 */
@RestControllerAdvice(basePackages = "pe.ayni.recognition.interfaces.rest")
class RecognitionExceptionHandler {

  private final Clock clock;

  RecognitionExceptionHandler(Clock clock) {
    this.clock = clock;
  }

  /** The person or the request does not exist in the requesting university. */
  @ExceptionHandler(NoSuchElementException.class)
  ResponseEntity<ApiError> handleNotFound(NoSuchElementException exception, HttpServletRequest request) {
    return answer(HttpStatus.NOT_FOUND, exception.getMessage(), request);
  }

  /** The student has not taught enough hours: nothing is registered, and they are told how many are missing. */
  @ExceptionHandler(InsufficientHours.class)
  ResponseEntity<InsufficientHoursError> handleInsufficientHours(
      InsufficientHours exception, HttpServletRequest request) {
    return ResponseEntity.status(HttpStatus.CONFLICT)
        .body(InsufficientHoursError.of(exception, request, clock.instant()));
  }

  /** Well formed, but the state of things refuses it, such as a university that has not opened recognition. */
  @ExceptionHandler(RecognitionStateConflict.class)
  ResponseEntity<ApiError> handleStateConflict(
      RecognitionStateConflict exception, HttpServletRequest request) {
    return answer(HttpStatus.CONFLICT, exception.getMessage(), request);
  }

  /** No university on the request: the header is missing. */
  @ExceptionHandler(MissingTenantException.class)
  ResponseEntity<ApiError> handleMissingTenant(
      MissingTenantException exception, HttpServletRequest request) {
    return answer(HttpStatus.BAD_REQUEST, exception.getMessage(), request);
  }

  /** No person on the request: the header is missing, or not a readable identifier. */
  @ExceptionHandler(MissingUserException.class)
  ResponseEntity<ApiError> handleMissingUser(MissingUserException exception, HttpServletRequest request) {
    return answer(HttpStatus.BAD_REQUEST, exception.getMessage(), request);
  }

  /** A header the endpoint needs was not sent. */
  @ExceptionHandler(MissingRequestHeaderException.class)
  ResponseEntity<ApiError> handleMissingHeader(
      MissingRequestHeaderException exception, HttpServletRequest request) {
    return answer(HttpStatus.BAD_REQUEST, "The header " + exception.getHeaderName() + " is required", request);
  }

  /** A body that is not JSON. */
  @ExceptionHandler(HttpMessageNotReadableException.class)
  ResponseEntity<ApiError> handleUnreadableBody(
      HttpMessageNotReadableException exception, HttpServletRequest request) {
    return answer(HttpStatus.BAD_REQUEST, "the request body could not be read", request);
  }

  /** A request body that failed {@code @Valid}. */
  @ExceptionHandler(MethodArgumentNotValidException.class)
  ResponseEntity<ApiError> handleInvalidBody(
      MethodArgumentNotValidException exception, HttpServletRequest request) {
    String message =
        exception.getBindingResult().getFieldErrors().stream()
            .findFirst()
            .map(error -> error.getField() + " " + error.getDefaultMessage())
            .orElse("the request body is invalid");
    return answer(HttpStatus.BAD_REQUEST, message, request);
  }

  /** A path or query parameter outside what the endpoint accepts. */
  @ExceptionHandler({ConstraintViolationException.class, MethodArgumentTypeMismatchException.class})
  ResponseEntity<ApiError> handleInvalidParameter(Exception exception, HttpServletRequest request) {
    return answer(HttpStatus.BAD_REQUEST, "a parameter of the request could not be read", request);
  }

  private ResponseEntity<ApiError> answer(HttpStatus status, String message, HttpServletRequest request) {
    return ResponseEntity.status(status).body(ApiError.of(status, message, request, clock.instant()));
  }
}
