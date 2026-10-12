package pe.ayni.audit.interfaces.rest;

import jakarta.servlet.http.HttpServletRequest;
import java.time.Clock;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import pe.ayni.audit.domain.model.AnomalyAlreadyResolved;
import pe.ayni.audit.domain.model.AnomalyNotFound;
import pe.ayni.audit.domain.model.AuditAccessDenied;
import pe.ayni.audit.domain.model.InvalidDismissalReason;
import pe.ayni.audit.domain.model.InvalidPeriod;
import pe.ayni.audit.domain.model.InvalidStatusFilter;
import pe.ayni.shared.tenancy.MissingTenantException;
import pe.ayni.shared.tenancy.MissingUserException;

/**
 * Turns the exceptions of this module into answers, for the controllers of this module alone.
 *
 * <p>A person who is not an active coordinator is refused with a 403, for the log and for the
 * alerts alike.
 */
@RestControllerAdvice(basePackages = "pe.ayni.audit.interfaces.rest")
class AuditExceptionHandler {

  private final Clock clock;

  AuditExceptionHandler(Clock clock) {
    this.clock = clock;
  }

  /** Reading the log is for active coordinators of the university. */
  @ExceptionHandler(AuditAccessDenied.class)
  ResponseEntity<ApiError> handleDenied(AuditAccessDenied exception, HttpServletRequest request) {
    return answer(HttpStatus.FORBIDDEN, exception.getMessage(), request);
  }

  /** There is no such alert in this university, and another university's one is no different. */
  @ExceptionHandler(AnomalyNotFound.class)
  ResponseEntity<ApiError> handleNotFound(AnomalyNotFound exception, HttpServletRequest request) {
    return answer(HttpStatus.NOT_FOUND, exception.getMessage(), request);
  }

  /** The alert was decided before: it is not decided again. */
  @ExceptionHandler(AnomalyAlreadyResolved.class)
  ResponseEntity<ApiError> handleAlreadyResolved(
      AnomalyAlreadyResolved exception, HttpServletRequest request) {
    return answer(HttpStatus.CONFLICT, exception.getMessage(), request);
  }

  /** A dismissal without a reason, or with one that is too long. */
  @ExceptionHandler(InvalidDismissalReason.class)
  ResponseEntity<ApiError> handleInvalidReason(
      InvalidDismissalReason exception, HttpServletRequest request) {
    return answer(HttpStatus.BAD_REQUEST, exception.getMessage(), request);
  }

  /** A status that an alert cannot have. */
  @ExceptionHandler(InvalidStatusFilter.class)
  ResponseEntity<ApiError> handleInvalidStatus(
      InvalidStatusFilter exception, HttpServletRequest request) {
    return answer(HttpStatus.BAD_REQUEST, exception.getMessage(), request);
  }

  /** A body that is missing or is not JSON. */
  @ExceptionHandler(HttpMessageNotReadableException.class)
  ResponseEntity<ApiError> handleUnreadableBody(
      HttpMessageNotReadableException exception, HttpServletRequest request) {
    return answer(HttpStatus.BAD_REQUEST, "The body of the request could not be read", request);
  }

  /** A period that ends before it starts. */
  @ExceptionHandler(InvalidPeriod.class)
  ResponseEntity<ApiError> handleInvalidPeriod(InvalidPeriod exception, HttpServletRequest request) {
    return answer(HttpStatus.BAD_REQUEST, exception.getMessage(), request);
  }

  /** No university on the request: the header is missing. */
  @ExceptionHandler(MissingTenantException.class)
  ResponseEntity<ApiError> handleMissingTenant(
      MissingTenantException exception, HttpServletRequest request) {
    return answer(HttpStatus.BAD_REQUEST, exception.getMessage(), request);
  }

  /** No person on the request: the header is missing, or not a readable identifier. */
  @ExceptionHandler(MissingUserException.class)
  ResponseEntity<ApiError> handleMissingUser(
      MissingUserException exception, HttpServletRequest request) {
    return answer(HttpStatus.BAD_REQUEST, exception.getMessage(), request);
  }

  /** A header the endpoint needs was not sent. */
  @ExceptionHandler(MissingRequestHeaderException.class)
  ResponseEntity<ApiError> handleMissingHeader(
      MissingRequestHeaderException exception, HttpServletRequest request) {
    return answer(
        HttpStatus.BAD_REQUEST, "The header " + exception.getHeaderName() + " is required", request);
  }

  /** An identifier that is not a UUID, or a date that is not ISO 8601. */
  @ExceptionHandler(MethodArgumentTypeMismatchException.class)
  ResponseEntity<ApiError> handleInvalidParameter(
      MethodArgumentTypeMismatchException exception, HttpServletRequest request) {
    return answer(
        HttpStatus.BAD_REQUEST,
        "The parameter " + exception.getName() + " could not be read",
        request);
  }

  private ResponseEntity<ApiError> answer(
      HttpStatus status, String message, HttpServletRequest request) {
    return ResponseEntity.status(status).body(ApiError.of(status, message, request, clock.instant()));
  }
}
