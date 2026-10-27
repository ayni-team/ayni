package pe.ayni.recognition.interfaces.rest;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pe.ayni.recognition.application.CurrentRuleQuery;
import pe.ayni.recognition.application.SetRecognitionRuleUseCase;
import pe.ayni.shared.tenancy.CurrentUser;

/**
 * What a coordinator asks of the students of their university for recognition: the hours that open
 * the way to a request.
 *
 * <p>Every endpoint answers for the coordinator making the request, read from {@link CurrentUser},
 * and the use case checks that they are an active one.
 */
@RestController
@RequestMapping("/api/v1/coordinator/recognition/rule")
@Tag(name = "Coordinator recognition", description = "Reviewing and deciding recognition requests")
class CoordinatorRecognitionRuleController {

  private final CurrentRuleQuery currentRule;
  private final SetRecognitionRuleUseCase setRule;

  CoordinatorRecognitionRuleController(CurrentRuleQuery currentRule, SetRecognitionRuleUseCase setRule) {
    this.currentRule = currentRule;
    this.setRule = setRule;
  }

  @GetMapping
  @Operation(
      summary = "The recognition rule in force at the university",
      description =
          """
          US27, US28: the hours the university asks of a student before it considers recognition, \
          and the day it started to apply. Until a coordinator sets one, recognition is not open: \
          students see no requirement and cannot submit a request.
          """)
  @ApiResponse(
      responseCode = "200",
      description = "The rule in force today in the university's calendar",
      content =
          @Content(
              schema = @Schema(implementation = RuleResponse.class),
              examples = @ExampleObject(value = "{\"minimumHours\": 20, \"validFrom\": \"2026-10-20\"}")))
  @ApiResponse(
      responseCode = "403",
      description = "The person asking is not an active coordinator",
      content = @Content(schema = @Schema(implementation = ApiError.class)))
  @ApiResponse(
      responseCode = "404",
      description = "The university has not set a rule",
      content = @Content(schema = @Schema(implementation = ApiError.class)))
  RuleResponse current() {
    return RuleResponse.of(currentRule.of(CurrentUser.require()));
  }

  @PutMapping
  @Operation(
      summary = "Sets the recognition rule of the university",
      description =
          """
          US27, US28: the coordinator says how many hours of verified sessions a student must have \
          taught, from a day of the university's calendar (today when omitted, never in the past). \
          The rule in force is closed and the new one added in one step: rules are never edited, so \
          what students already submitted keeps the figures it was presented with.

          A rule that starts later leaves the current one running until that day. When two \
          coordinators set a rule at the same moment, both succeed one after the other and the last \
          one is the rule in force.
          """)
  @ApiResponse(
      responseCode = "200",
      description = "The rule that was added",
      content =
          @Content(
              schema = @Schema(implementation = RuleResponse.class),
              examples = @ExampleObject(value = "{\"minimumHours\": 20, \"validFrom\": \"2026-10-20\"}")))
  @ApiResponse(
      responseCode = "400",
      description = "The hours are not a whole number of at least 1, or the day is not a date or is past",
      content = @Content(schema = @Schema(implementation = ApiError.class)))
  @ApiResponse(
      responseCode = "403",
      description = "The person asking is not an active coordinator",
      content = @Content(schema = @Schema(implementation = ApiError.class)))
  RuleResponse set(
      @io.swagger.v3.oas.annotations.parameters.RequestBody(
              content =
                  @Content(
                      examples =
                          @ExampleObject(value = "{\"minimumHours\": 20, \"validFrom\": \"2026-11-01\"}")))
          @Valid
          @RequestBody
          RuleRequest body) {
    return RuleResponse.of(setRule.execute(CurrentUser.require(), body.minimumHours(), body.validFrom()));
  }
}
