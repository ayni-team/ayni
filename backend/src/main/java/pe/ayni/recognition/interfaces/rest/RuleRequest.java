package pe.ayni.recognition.interfaces.rest;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

/**
 * What the coordinator sets as the rule of the university.
 *
 * @param minimumHours hours of verified sessions a student must have taught
 * @param validFrom the first day it applies, in the university's calendar; today when absent
 */
@Schema(name = "RecognitionRuleRequest", description = "Sets what the university asks for recognition")
public record RuleRequest(
    @NotNull @Min(1) @Schema(example = "20") Integer minimumHours,
    @Schema(nullable = true, example = "2026-11-01") LocalDate validFrom) {}
