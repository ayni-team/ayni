package pe.ayni.skills.interfaces.rest;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import java.time.Clock;
import java.util.NoSuchElementException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import pe.ayni.shared.tenancy.MissingTenantException;
import pe.ayni.shared.tenancy.MissingUserException;
import pe.ayni.skills.domain.model.SkillsRuleViolation;

/**
 * Turns the exceptions of this module into answers.
 *
 * <p>It is declared for skills' controller alone, so each module keeps its own mapping: a refusal
 * that means one thing here should not quietly acquire a status code decided elsewhere.
 */
@RestControllerAdvice(assignableTypes = SkillsController.class)
class SkillsExceptionHandler {

  private final Clock clock;

  SkillsExceptionHandler(Clock clock) {
    this.clock = clock;
  }

  /** The catalogue item does not exist, or is not visible to the requesting university. */
  @ExceptionHandler(NoSuchElementException.class)
  ResponseEntity<ApiError> handleNotFound(NoSuchElementException exception, HttpServletRequest request) {
    return answer(HttpStatus.NOT_FOUND, exception.getMessage(), request);
  }

  /**
   * A rule of this module refusing what was asked, such as a grade below the threshold.
   *
   * <p>Only skills' own refusals are answered here. {@code IllegalArgumentException} used to be
   * mapped instead, which turned every programming mistake below the controller into a 400 and hid
   * it: a bug that answers "bad request" is a bug nobody reports.
   */
  @ExceptionHandler(SkillsRuleViolation.class)
  ResponseEntity<ApiError> handleSkillsRuleViolation(
      SkillsRuleViolation exception, HttpServletRequest request) {
    return answer(HttpStatus.BAD_REQUEST, exception.getMessage(), request);
  }

  /** No university on the request: the header is missing. */
  @ExceptionHandler(MissingTenantException.class)
  ResponseEntity<ApiError> handleMissingTenant(
      MissingTenantException exception, HttpServletRequest request) {
    return answer(HttpStatus.BAD_REQUEST, exception.getMessage(), request);
  }

  /** No tutor on the request: the header is missing, or not a readable identifier. */
  @ExceptionHandler(MissingUserException.class)
  ResponseEntity<ApiError> handleMissingUser(
      MissingUserException exception, HttpServletRequest request) {
    return answer(HttpStatus.BAD_REQUEST, exception.getMessage(), request);
  }

  /** A header the endpoint needs was not sent. */
  @ExceptionHandler(MissingRequestHeaderException.class)
  ResponseEntity<ApiError> handleMissingHeader(
      MissingRequestHeaderException exception, HttpServletRequest request) {
    return answer(HttpStatus.BAD_REQUEST, "The header " + exception.getHeaderName() + " is required", request);
  }

  /** A request body that failed `@Valid`, such as a missing `catalogItemId`. */
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
  @ExceptionHandler(ConstraintViolationException.class)
  ResponseEntity<ApiError> handleInvalidParameter(
      ConstraintViolationException exception, HttpServletRequest request) {
    return answer(HttpStatus.BAD_REQUEST, exception.getMessage(), request);
  }

  private ResponseEntity<ApiError> answer(HttpStatus status, String message, HttpServletRequest request) {
    return ResponseEntity.status(status).body(ApiError.of(status, message, request, clock.instant()));
  }
}
