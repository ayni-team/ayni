package pe.ayni.config.demo;

import jakarta.servlet.http.HttpServletRequest;
import java.time.Clock;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** The errors of the endpoints of the demonstration, with the shape of every error. */
@Profile("demo")
@RestControllerAdvice(assignableTypes = DemoClockController.class)
class DemoExceptionHandler {

  private final Clock clock;

  DemoExceptionHandler(Clock clock) {
    this.clock = clock;
  }

  @ExceptionHandler(InvalidClockValue.class)
  ResponseEntity<DemoApiError> invalidValue(
      InvalidClockValue exception, HttpServletRequest request) {
    return answer(exception.getMessage(), request);
  }

  @ExceptionHandler(HttpMessageNotReadableException.class)
  ResponseEntity<DemoApiError> unreadableBody(
      HttpMessageNotReadableException exception, HttpServletRequest request) {
    return answer("The body is missing or is not the JSON this endpoint takes", request);
  }

  private ResponseEntity<DemoApiError> answer(String message, HttpServletRequest request) {
    return ResponseEntity.badRequest()
        .body(DemoApiError.of(HttpStatus.BAD_REQUEST, message, request, clock.instant()));
  }
}
