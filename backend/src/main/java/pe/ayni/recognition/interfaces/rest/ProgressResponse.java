package pe.ayni.recognition.interfaces.rest;

import io.swagger.v3.oas.annotations.media.Schema;
import pe.ayni.recognition.domain.model.RecognitionProgress;

/**
 * How far a student is from the hours their university asks for.
 *
 * @param earnedHours hours taught in verified sessions that no request has used yet
 * @param requiredHours what the university asks for, absent while it has not opened recognition
 * @param missingHours hours still to teach, zero once reached, absent without a rule
 * @param canRequest whether the student can ask their university for recognition now
 */
@Schema(name = "RecognitionProgress", description = "Hours taught against the hours the university asks for")
public record ProgressResponse(
    @Schema(example = "14") int earnedHours,
    @Schema(example = "9") int sessionsCount,
    @Schema(nullable = true, example = "20") Integer requiredHours,
    @Schema(nullable = true, example = "6") Integer missingHours,
    @Schema(example = "false") boolean requirementMet,
    @Schema(example = "false") boolean canRequest) {

  static ProgressResponse of(RecognitionProgress progress) {
    return new ProgressResponse(
        progress.earnedHours(),
        progress.sessionsCount(),
        progress.requiredHours(),
        progress.missingHours(),
        progress.requirementMet(),
        progress.requirementMet());
  }
}
