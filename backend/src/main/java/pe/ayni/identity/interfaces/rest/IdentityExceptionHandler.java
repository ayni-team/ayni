package pe.ayni.identity.interfaces.rest;

import jakarta.servlet.http.HttpServletRequest;
import java.time.Clock;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import pe.ayni.identity.domain.model.IdentityRuleViolation;
import java.util.NoSuchElementException;
import pe.ayni.shared.tenancy.MissingTenantException;
import pe.ayni.shared.tenancy.MissingUserException;

@RestControllerAdvice(
        assignableTypes = {
                AccessController.class,
                IdentityMeController.class
        })
class IdentityExceptionHandler {

    private final Clock clock;

    IdentityExceptionHandler(Clock clock) {
        this.clock = clock;
    }

    @ExceptionHandler(IdentityRuleViolation.class)
    ResponseEntity<ApiError> handleIdentityRuleViolation(
            IdentityRuleViolation exception,
            HttpServletRequest request) {

        return answer(
                HttpStatus.BAD_REQUEST,
                exception.getMessage(),
                request);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ApiError> handleInvalidRequest(
            MethodArgumentNotValidException exception,
            HttpServletRequest request) {

        String message =
                exception.getBindingResult().getFieldErrors().stream()
                        .findFirst()
                        .map(error -> error.getField() + " " + error.getDefaultMessage())
                        .orElse("The request is invalid");

        return answer(
                HttpStatus.BAD_REQUEST,
                message,
                request);
    }

    private ResponseEntity<ApiError> answer(
            HttpStatus status,
            String message,
            HttpServletRequest request) {

        return ResponseEntity.status(status)
                .body(ApiError.of(
                        status,
                        message,
                        request,
                        clock.instant()));
    }
    @ExceptionHandler(NoSuchElementException.class)
    ResponseEntity<ApiError> handleNotFound(
            NoSuchElementException exception,
            HttpServletRequest request) {

        return answer(
                HttpStatus.NOT_FOUND,
                exception.getMessage(),
                request);
    }

    @ExceptionHandler({
            MissingTenantException.class,
            MissingUserException.class
    })
    ResponseEntity<ApiError> handleMissingContext(
            RuntimeException exception,
            HttpServletRequest request) {

        return answer(
                HttpStatus.BAD_REQUEST,
                exception.getMessage(),
                request);
    }
}