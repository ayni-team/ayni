package pe.ayni.recognition.interfaces.rest;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import pe.ayni.recognition.application.ResolveRecognitionRequestUseCase.Decision;

/**
 * What the coordinator decides about a request.
 *
 * @param decision APPROVE or REJECT
 * @param reason why, as the student will read it: required for both
 */
@Schema(name = "RecognitionDecisionRequest", description = "Approves or rejects a recognition request")
public record DecisionRequest(
    @NotNull @Schema(allowableValues = {"APPROVE", "REJECT"}, example = "APPROVE") Decision decision,
    @NotBlank @Size(max = 1000) @Schema(example = "The hours match the extracurricular credit of the programme")
        String reason) {}
