package pe.ayni.skills.interfaces.rest;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.util.List;
import org.springframework.http.HttpStatus;
import pe.ayni.skills.domain.model.SimilarSkillsFound;

/**
 * The answer to a proposal the catalogue already seems to cover: the usual five fields of an error,
 * plus the skills that look like it, so the student can see them before deciding.
 */
@Schema(name = "SimilarSkillsError", description = "A proposal that looks like skills already in the catalogue")
public record SimilarSkillsError(
    @Schema(example = "2026-10-04T15:00:00Z") Instant timestamp,
    @Schema(example = "409") int status,
    @Schema(example = "Conflict") String error,
    String message,
    @Schema(example = "/api/v1/tutor/skills/proposals") String path,
    List<CatalogItemResponse> similar) {

  static SimilarSkillsError of(SimilarSkillsFound found, HttpServletRequest request, Instant now) {
    return new SimilarSkillsError(
        now,
        HttpStatus.CONFLICT.value(),
        HttpStatus.CONFLICT.getReasonPhrase(),
        found.getMessage(),
        request.getRequestURI(),
        found.getSimilar().stream().map(CatalogItemResponse::of).toList());
  }
}
