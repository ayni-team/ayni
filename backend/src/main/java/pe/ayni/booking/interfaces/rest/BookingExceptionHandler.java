package pe.ayni.booking.interfaces.rest;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import java.time.Clock;
import java.util.NoSuchElementException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import pe.ayni.booking.application.BookingFailed;
import pe.ayni.booking.domain.model.BookingRuleViolation;
import pe.ayni.booking.domain.model.HoldExpired;
import pe.ayni.booking.domain.model.HourUnavailable;
import pe.ayni.booking.domain.model.HoursNotOffered;
import pe.ayni.booking.domain.model.StudentNotActive;
import pe.ayni.booking.domain.model.TutorNotBookable;
import pe.ayni.shared.tenancy.MissingTenantException;
import pe.ayni.shared.tenancy.MissingUserException;
import pe.ayni.wallet.InsufficientCreditsException;

/**
 * Maps booking refusals and invalid HTTP input to the module's common error shape.
 *
 * <p>400 is for a request that is wrong whatever the state of the world. 409 is for a well formed
 * request that the state of the tutor's agenda refuses: somebody got to the hour first, the hold ran
 * out, the tutor does not offer those hours.
 */
@RestControllerAdvice(assignableTypes = {AvailabilityController.class, BookingController.class})
class BookingExceptionHandler {

  /** What a student is told when they lose the race to hold an hour. */
  static final String TAKEN_MEANWHILE =
      "This hour was just taken by another student; choose another one";

  private final Clock clock;

  BookingExceptionHandler(Clock clock) {
    this.clock = clock;
  }

  @ExceptionHandler(BookingRuleViolation.class)
  ResponseEntity<ApiError> handleBookingRuleViolation(
      BookingRuleViolation exception, HttpServletRequest request) {
    return answer(HttpStatus.BAD_REQUEST, exception.getMessage(), request);
  }

  @ExceptionHandler({
    HourUnavailable.class,
    HoldExpired.class,
    HoursNotOffered.class,
    TutorNotBookable.class
  })
  ResponseEntity<ApiError> handleAgendaConflict(
      BookingRuleViolation exception, HttpServletRequest request) {
    return answer(HttpStatus.CONFLICT, exception.getMessage(), request);
  }

  /**
   * The balance does not cover the hours. Wallet says how many credits are missing and the student
   * is told that number, so they know what they are short of rather than only that they are.
   */
  @ExceptionHandler(InsufficientCreditsException.class)
  ResponseEntity<ApiError> handleInsufficientCredits(
      InsufficientCreditsException exception, HttpServletRequest request) {
    int missing = exception.missing().amount();
    return answer(
        HttpStatus.CONFLICT,
        "Not enough credits: "
            + missing
            + (missing == 1 ? " more is" : " more are")
            + " needed to book these hours",
        request);
  }

  /** The subject asked for is not one the student's university can see. */
  @ExceptionHandler(NoSuchElementException.class)
  ResponseEntity<ApiError> handleNotFound(
      NoSuchElementException exception, HttpServletRequest request) {
    return answer(HttpStatus.NOT_FOUND, exception.getMessage(), request);
  }

  /**
   * A confirmation failed for no reason anybody chose. The use case already rolled it back and gave
   * the holds back, and its message says so; the cause was logged where it happened.
   */
  @ExceptionHandler(BookingFailed.class)
  ResponseEntity<ApiError> handleBookingFailed(
      BookingFailed exception, HttpServletRequest request) {
    return answer(HttpStatus.INTERNAL_SERVER_ERROR, exception.getMessage(), request);
  }

  /** Two requests read the same hour free and this one wrote second: the version column refused it. */
  @ExceptionHandler(OptimisticLockingFailureException.class)
  ResponseEntity<ApiError> handleLostRace(
      OptimisticLockingFailureException exception, HttpServletRequest request) {
    return answer(HttpStatus.CONFLICT, TAKEN_MEANWHILE, request);
  }

  @ExceptionHandler(StudentNotActive.class)
  ResponseEntity<ApiError> handleStudentNotActive(
      StudentNotActive exception, HttpServletRequest request) {
    return answer(HttpStatus.FORBIDDEN, exception.getMessage(), request);
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
    return answer(
        HttpStatus.BAD_REQUEST,
        "The request body could not be read. Dates travel in ISO 8601 UTC",
        request);
  }

  @ExceptionHandler(ConstraintViolationException.class)
  ResponseEntity<ApiError> handleInvalidParameter(
      ConstraintViolationException exception, HttpServletRequest request) {
    return answer(HttpStatus.BAD_REQUEST, exception.getMessage(), request);
  }

  @ExceptionHandler(HandlerMethodValidationException.class)
  ResponseEntity<ApiError> handleInvalidParameters(
      HandlerMethodValidationException exception, HttpServletRequest request) {
    return answer(HttpStatus.BAD_REQUEST, "A parameter is invalid", request);
  }

  @ExceptionHandler(MissingServletRequestParameterException.class)
  ResponseEntity<ApiError> handleMissingParameter(
      MissingServletRequestParameterException exception, HttpServletRequest request) {
    return answer(
        HttpStatus.BAD_REQUEST,
        "The parameter " + exception.getParameterName() + " is required",
        request);
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
