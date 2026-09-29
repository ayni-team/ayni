package pe.ayni.sessions.interfaces.rest;

import jakarta.servlet.http.HttpServletRequest;
import java.time.Clock;
import java.util.NoSuchElementException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import pe.ayni.sessions.domain.model.NotAParticipant;
import pe.ayni.sessions.domain.model.PresenceCheckUnavailable;
import pe.ayni.sessions.domain.model.SessionNotOpen;
import pe.ayni.sessions.domain.model.SessionRuleViolation;
import pe.ayni.sessions.domain.model.WrongPresenceCode;
import pe.ayni.shared.tenancy.MissingTenantException;
import pe.ayni.shared.tenancy.MissingUserException;

/**
 * Maps the session's refusals and invalid HTTP input to the common error shape: timestamp, status,
 * error, message, path.
 *
 * <p>403 for somebody who does not belong to the session, 409 for a participant arriving when the
 * room is not open or confirming presence when it cannot be confirmed, 422 for a presence code that
 * is well formed but wrong, 404 for a session the university does not have. Scoped to this module's
 * controller, like every module's handler.
 */
@RestControllerAdvice(assignableTypes = SessionsController.class)
class SessionsExceptionHandler {

  private final Clock clock;

  SessionsExceptionHandler(Clock clock) {
    this.clock = clock;
  }

  @ExceptionHandler(NotAParticipant.class)
  ResponseEntity<ApiError> handleNotAParticipant(
      NotAParticipant exception, HttpServletRequest request) {
    return answer(HttpStatus.FORBIDDEN, exception.getMessage(), request);
  }

  @ExceptionHandler(SessionNotOpen.class)
  ResponseEntity<ApiError> handleNotOpen(SessionNotOpen exception, HttpServletRequest request) {
    return answer(HttpStatus.CONFLICT, exception.getMessage(), request);
  }

  @ExceptionHandler(PresenceCheckUnavailable.class)
  ResponseEntity<ApiError> handlePresenceUnavailable(
      PresenceCheckUnavailable exception, HttpServletRequest request) {
    return answer(HttpStatus.CONFLICT, exception.getMessage(), request);
  }

  /** Six digits, just not the right ones: told apart from a malformed code, which is 400. */
  @ExceptionHandler(WrongPresenceCode.class)
  ResponseEntity<ApiError> handleWrongCode(
      WrongPresenceCode exception, HttpServletRequest request) {
    return answer(HttpStatus.UNPROCESSABLE_ENTITY, exception.getMessage(), request);
  }

  @ExceptionHandler(SessionRuleViolation.class)
  ResponseEntity<ApiError> handleRuleViolation(
      SessionRuleViolation exception, HttpServletRequest request) {
    return answer(HttpStatus.BAD_REQUEST, exception.getMessage(), request);
  }

  @ExceptionHandler(NoSuchElementException.class)
  ResponseEntity<ApiError> handleNotFound(
      NoSuchElementException exception, HttpServletRequest request) {
    return answer(HttpStatus.NOT_FOUND, exception.getMessage(), request);
  }

  @ExceptionHandler(MissingTenantException.class)
  ResponseEntity<ApiError> handleMissingTenant(
      MissingTenantException exception, HttpServletRequest request) {
    return answer(HttpStatus.BAD_REQUEST, exception.getMessage(), request);
  }

  @ExceptionHandler(MissingUserException.class)
  ResponseEntity<ApiError> handleMissingUser(
      MissingUserException exception, HttpServletRequest request) {
    return answer(HttpStatus.BAD_REQUEST, exception.getMessage(), request);
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  ResponseEntity<ApiError> handleInvalidBody(
      MethodArgumentNotValidException exception, HttpServletRequest request) {
    String message =
        exception.getBindingResult().getFieldErrors().stream()
            .findFirst()
            .map(error -> error.getField() + " " + error.getDefaultMessage())
            .orElse("The request body is invalid");
    return answer(HttpStatus.BAD_REQUEST, message, request);
  }

  @ExceptionHandler(HttpMessageNotReadableException.class)
  ResponseEntity<ApiError> handleUnreadableBody(
      HttpMessageNotReadableException exception, HttpServletRequest request) {
    return answer(HttpStatus.BAD_REQUEST, "The request body could not be read", request);
  }

  @ExceptionHandler(MethodArgumentTypeMismatchException.class)
  ResponseEntity<ApiError> handleUnreadableParameter(
      MethodArgumentTypeMismatchException exception, HttpServletRequest request) {
    return answer(
        HttpStatus.BAD_REQUEST,
        "The value of " + exception.getName() + " could not be read",
        request);
  }

  private ResponseEntity<ApiError> answer(
      HttpStatus status, String message, HttpServletRequest request) {
    return ResponseEntity.status(status)
        .body(ApiError.of(status, message, request, clock.instant()));
  }
}
