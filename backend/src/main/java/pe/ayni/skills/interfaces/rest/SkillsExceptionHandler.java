package pe.ayni.skills.interfaces.rest;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import java.time.Clock;
import java.util.NoSuchElementException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import pe.ayni.shared.tenancy.MissingTenantException;
import pe.ayni.shared.tenancy.MissingUserException;
import pe.ayni.skills.domain.model.NotACoordinator;
import pe.ayni.skills.domain.model.NotTheOwner;
import pe.ayni.skills.domain.model.SimilarSkillsFound;
import pe.ayni.skills.domain.model.SkillsRuleViolation;
import pe.ayni.skills.domain.model.SkillsStateConflict;

/**
 * Turns the exceptions of this module into answers.
 *
 * <p>It is declared for skills' controller alone, so each module keeps its own mapping: a refusal
 * that means one thing here should not quietly acquire a status code decided elsewhere.
 */
@RestControllerAdvice(
    assignableTypes = {
      SkillsController.class,
      CoordinatorValidationsController.class,
      CoordinatorSkillProposalsController.class
    })
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

  /** Reviewing is for coordinators: the person is known in this university but is not one. */
  @ExceptionHandler(NotACoordinator.class)
  ResponseEntity<ApiError> handleNotACoordinator(
      NotACoordinator exception, HttpServletRequest request) {
    return answer(HttpStatus.FORBIDDEN, exception.getMessage(), request);
  }

  /** A body that is not JSON, or a decision that is neither APPROVE nor REJECT. */
  @ExceptionHandler(HttpMessageNotReadableException.class)
  ResponseEntity<ApiError> handleUnreadableBody(
      HttpMessageNotReadableException exception, HttpServletRequest request) {
    return answer(HttpStatus.BAD_REQUEST, "the request body could not be read", request);
  }

  /** The skill exists in this university but is another tutor's: not allowed, which is not 404. */
  @ExceptionHandler(NotTheOwner.class)
  ResponseEntity<ApiError> handleNotTheOwner(NotTheOwner exception, HttpServletRequest request) {
    return answer(HttpStatus.FORBIDDEN, exception.getMessage(), request);
  }

  /** A proposal that looks like what the catalogue has: a conflict that lists the similar skills. */
  @ExceptionHandler(SimilarSkillsFound.class)
  ResponseEntity<SimilarSkillsError> handleSimilarSkills(
      SimilarSkillsFound exception, HttpServletRequest request) {
    return ResponseEntity.status(HttpStatus.CONFLICT)
        .body(SimilarSkillsError.of(exception, request, clock.instant()));
  }

  /** Well formed, but the state of the skill refuses it, such as withdrawing one already withdrawn. */
  @ExceptionHandler(SkillsStateConflict.class)
  ResponseEntity<ApiError> handleStateConflict(
      SkillsStateConflict exception, HttpServletRequest request) {
    return answer(HttpStatus.CONFLICT, exception.getMessage(), request);
  }

  /** The files of a submission were not sent at all. */
  @ExceptionHandler({
    MissingServletRequestPartException.class,
    MissingServletRequestParameterException.class
  })
  ResponseEntity<ApiError> handleMissingPart(Exception exception, HttpServletRequest request) {
    return answer(HttpStatus.BAD_REQUEST, "attach at least one file: a portfolio or a certificate", request);
  }

  /** A path parameter that is not an identifier. */
  @ExceptionHandler(MethodArgumentTypeMismatchException.class)
  ResponseEntity<ApiError> handleUnreadableParameter(
      MethodArgumentTypeMismatchException exception, HttpServletRequest request) {
    return answer(
        HttpStatus.BAD_REQUEST, "The value of " + exception.getName() + " could not be read", request);
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
