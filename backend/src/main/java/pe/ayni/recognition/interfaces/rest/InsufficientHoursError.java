package pe.ayni.recognition.interfaces.rest;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import org.springframework.http.HttpStatus;
import pe.ayni.recognition.domain.model.InsufficientHours;

/** The usual error, plus how many hours the student still has to teach. */
@Schema(name = "InsufficientHoursError", description = "The student has not taught the hours asked for")
public record InsufficientHoursError(
    @Schema(example = "2026-10-05T19:08:39Z") Instant timestamp,
    @Schema(example = "409") int status,
    @Schema(example = "Conflict") String error,
    @Schema(example = "4 more hours are needed before the recognition can be requested") String message,
    @Schema(example = "/api/v1/recognition/requests") String path,
    @Schema(example = "4") int missingHours) {

  static InsufficientHoursError of(InsufficientHours exception, HttpServletRequest request, Instant now) {
    HttpStatus status = HttpStatus.CONFLICT;
    return new InsufficientHoursError(
        now,
        status.value(),
        status.getReasonPhrase(),
        exception.getMessage(),
        request.getRequestURI(),
        exception.getMissingHours());
  }
}
