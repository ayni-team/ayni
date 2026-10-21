package pe.ayni.reputation.interfaces.rest;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import java.time.Clock;
import java.util.NoSuchElementException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import pe.ayni.reputation.domain.model.AlreadyRated;
import pe.ayni.reputation.domain.model.InvalidRating;
import pe.ayni.reputation.domain.model.NotAParticipant;
import pe.ayni.reputation.domain.model.SessionNotRatable;
import pe.ayni.shared.tenancy.MissingTenantException;
import pe.ayni.shared.tenancy.MissingUserException;

/**
 * Maps reputation's refusals and invalid HTTP input to the common error shape: timestamp, status,
 * error, message, path.
 *
 * <p>Scoped to this module's controllers, so that one module's decision about an exception never
 * answers for another module's endpoints.
 */
@RestControllerAdvice(
        assignableTypes = {
                TutorStandingController.class,
                RatingController.class,
                TutorReputationController.class
        })
class ReputationExceptionHandler {

    private final Clock clock;

    ReputationExceptionHandler(Clock clock) {
        this.clock = clock;
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

    @ExceptionHandler(MissingTenantException.class)
    ResponseEntity<ApiError> handleMissingTenant(
            MissingTenantException exception,
            HttpServletRequest request) {

        return answer(
                HttpStatus.BAD_REQUEST,
                exception.getMessage(),
                request);
    }

    @ExceptionHandler(MissingUserException.class)
    ResponseEntity<ApiError> handleMissingUser(
            MissingUserException exception,
            HttpServletRequest request) {

        return answer(
                HttpStatus.BAD_REQUEST,
                exception.getMessage(),
                request);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    ResponseEntity<ApiError> handleInvalidParameter(
            ConstraintViolationException exception,
            HttpServletRequest request) {

        return answer(
                HttpStatus.BAD_REQUEST,
                exception.getMessage(),
                request);
    }

    @ExceptionHandler(HandlerMethodValidationException.class)
    ResponseEntity<ApiError> handleInvalidParameters(
            HandlerMethodValidationException exception,
            HttpServletRequest request) {

        return answer(
                HttpStatus.BAD_REQUEST,
                "A parameter is invalid",
                request);
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    ResponseEntity<ApiError> handleMissingParameter(
            MissingServletRequestParameterException exception,
            HttpServletRequest request) {

        return answer(
                HttpStatus.BAD_REQUEST,
                "The parameter " + exception.getParameterName() + " is required",
                request);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    ResponseEntity<ApiError> handleUnreadableParameter(
            MethodArgumentTypeMismatchException exception,
            HttpServletRequest request) {

        return answer(
                HttpStatus.BAD_REQUEST,
                "The value of " + exception.getName() + " could not be read",
                request);
    }

    @ExceptionHandler(NotAParticipant.class)
    ResponseEntity<ApiError> handleNotAParticipant(
            NotAParticipant exception,
            HttpServletRequest request) {

        return answer(
                HttpStatus.FORBIDDEN,
                exception.getMessage(),
                request);
    }

    @ExceptionHandler(SessionNotRatable.class)
    ResponseEntity<ApiError> handleSessionNotRatable(
            SessionNotRatable exception,
            HttpServletRequest request) {

        return answer(
                HttpStatus.NOT_FOUND,
                exception.getMessage(),
                request);
    }

    @ExceptionHandler(AlreadyRated.class)
    ResponseEntity<ApiError> handleAlreadyRated(
            AlreadyRated exception,
            HttpServletRequest request) {

        return answer(
                HttpStatus.CONFLICT,
                exception.getMessage(),
                request);
    }

    @ExceptionHandler(InvalidRating.class)
    ResponseEntity<ApiError> handleInvalidRating(
            InvalidRating exception,
            HttpServletRequest request) {

        return answer(
                HttpStatus.BAD_REQUEST,
                exception.getMessage(),
                request);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ApiError> handleInvalidBody(
            MethodArgumentNotValidException exception,
            HttpServletRequest request) {

        return answer(
                HttpStatus.BAD_REQUEST,
                "The rating is invalid",
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