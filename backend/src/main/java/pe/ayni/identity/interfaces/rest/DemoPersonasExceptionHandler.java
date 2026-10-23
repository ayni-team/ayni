package pe.ayni.identity.interfaces.rest;

import jakarta.servlet.http.HttpServletRequest;
import java.time.Clock;
import java.util.NoSuchElementException;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** The errors of acting as a persona, with the shape of every error. */
@Profile("demo")
@RestControllerAdvice(assignableTypes = DemoPersonasController.class)
class DemoPersonasExceptionHandler {

    private final Clock clock;

    DemoPersonasExceptionHandler(Clock clock) {
        this.clock = clock;
    }

    @ExceptionHandler(NoSuchElementException.class)
    ResponseEntity<ApiError> notFound(
            NoSuchElementException exception,
            HttpServletRequest request) {

        return answer(HttpStatus.NOT_FOUND, exception.getMessage(), request);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ApiError> invalidRequest(
            MethodArgumentNotValidException exception,
            HttpServletRequest request) {

        // The message names the field and the rule, never the value that was sent.
        String message =
                exception.getBindingResult().getFieldErrors().stream()
                        .findFirst()
                        .map(error -> error.getField() + " " + error.getDefaultMessage())
                        .orElse("The request is invalid");

        return answer(HttpStatus.BAD_REQUEST, message, request);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<ApiError> unreadableBody(
            HttpMessageNotReadableException exception,
            HttpServletRequest request) {

        return answer(
                HttpStatus.BAD_REQUEST,
                "The body is missing or is not the JSON this endpoint takes",
                request);
    }

    private ResponseEntity<ApiError> answer(
            HttpStatus status,
            String message,
            HttpServletRequest request) {

        return ResponseEntity.status(status)
                .body(ApiError.of(status, message, request, clock.instant()));
    }
}
