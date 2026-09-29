package pe.ayni.reputation.interfaces.rest;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import org.springframework.http.HttpStatus;

/** The error shape returned by reputation endpoints. */
@Schema(name = "ReputationApiError", description = "What went wrong")
record ApiError(
        @Schema(example = "2026-09-29T21:00:00Z") Instant timestamp,
        @Schema(example = "404") int status,
        @Schema(example = "Not Found") String error,
        @Schema(example = "The tutor does not teach this course: there is no standing to show")
                String message,
        @Schema(example = "/api/v1/tutors/22222222-2222-4222-8222-222222222222/standing")
                String path) {

    static ApiError of(HttpStatus status, String message, HttpServletRequest request, Instant now) {
        return new ApiError(
                now, status.value(), status.getReasonPhrase(), message, request.getRequestURI());
    }
}
