package pe.ayni.notifications.interfaces.rest;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import java.time.Clock;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import pe.ayni.notifications.domain.model.NotificationNotFoundException;

/**
 * Maps this module's refusals and invalid HTTP input to the common error shape: timestamp, status,
 * error, message, path.
 *
 * <p>404 for a notice the person does not have in their university, whoever else it belongs to,
 * and 400 for a page or a size outside what the list accepts. Scoped to this module's controller,
 * like every module's handler.
 */
@RestControllerAdvice(assignableTypes = NotificationsController.class)
class NotificationsExceptionHandler {

  private final Clock clock;

  NotificationsExceptionHandler(Clock clock) {
    this.clock = clock;
  }

  @ExceptionHandler(NotificationNotFoundException.class)
  ResponseEntity<ApiError> handleNotFound(
      NotificationNotFoundException exception, HttpServletRequest request) {
    return answer(HttpStatus.NOT_FOUND, exception.getMessage(), request);
  }

  /** A notice identifier that is not a UUID, or a flag that is not a boolean. */
  @ExceptionHandler(MethodArgumentTypeMismatchException.class)
  ResponseEntity<ApiError> handleUnreadableParameter(
      MethodArgumentTypeMismatchException exception, HttpServletRequest request) {
    return answer(
        HttpStatus.BAD_REQUEST,
        "The value of " + exception.getName() + " could not be read",
        request);
  }

  /** A page or a size outside what the endpoint accepts. */
  @ExceptionHandler({ConstraintViolationException.class, HandlerMethodValidationException.class})
  ResponseEntity<ApiError> handleInvalidParameter(Exception exception, HttpServletRequest request) {
    return answer(
        HttpStatus.BAD_REQUEST,
        "The page must be 0 or more and the size between 1 and 100",
        request);
  }

  private ResponseEntity<ApiError> answer(
      HttpStatus status, String message, HttpServletRequest request) {
    return ResponseEntity.status(status)
        .body(ApiError.of(status, message, request, clock.instant()));
  }
}
