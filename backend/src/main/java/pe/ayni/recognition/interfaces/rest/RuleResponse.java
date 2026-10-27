package pe.ayni.recognition.interfaces.rest;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import pe.ayni.recognition.domain.model.RecognitionRule;

/**
 * What the university asks of a student before it considers recognition.
 *
 * @param minimumHours hours of verified sessions the student must have taught
 * @param validFrom the first day it applies, in the university's calendar
 */
@Schema(name = "RecognitionRule", description = "What the university asks for recognition")
public record RuleResponse(
    @Schema(example = "20") int minimumHours, @Schema(example = "2026-10-20") LocalDate validFrom) {

  static RuleResponse of(RecognitionRule rule) {
    return new RuleResponse(rule.getMinimumHours(), rule.getValidFrom());
  }
}
