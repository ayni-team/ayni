package pe.ayni.identity.interfaces.rest;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import org.springframework.http.HttpStatus;

@Schema(name = "IdentityApiError", description = "What went wrong")
public record ApiError(
        @Schema(example = "2026-09-22T05:30:00Z") Instant timestamp,
        @Schema(example = "400") int status,
        @Schema(example = "Bad Request") String error,
        @Schema(example = "The institution is not affiliated with Ayni") String message,
        @Schema(example = "/api/v1/access/request") String path) {

    static ApiError of(
            HttpStatus status,
            String message,
            HttpServletRequest request,
            Instant now) {

        return new ApiError(
                now,
                status.value(),
                status.getReasonPhrase(),
                message,
                request.getRequestURI());
    }
}