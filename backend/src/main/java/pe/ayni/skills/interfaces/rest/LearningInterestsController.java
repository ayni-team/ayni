package pe.ayni.skills.interfaces.rest;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pe.ayni.shared.tenancy.CurrentUser;
import pe.ayni.skills.application.CompleteOnboardingUseCase;
import pe.ayni.skills.application.DeclareInterestsUseCase;
import pe.ayni.skills.application.LearningInterestsQuery;

/**
 * What a student needs help with and can teach: the last step of the onboarding (US40), and the
 * needs changed later on (US41).
 *
 * <p>Every endpoint answers about the student of the session, read from {@link CurrentUser}; none
 * takes a student as a parameter. What a student teaches after the onboarding is still managed
 * under {@code /api/v1/tutor/skills}.
 */
@RestController
@RequestMapping("/api/v1/me")
@Tag(name = "Learning interests", description = "What a student needs help with and can teach")
class LearningInterestsController {

  private static final String INTERESTS_EXAMPLE =
      """
      [{"id": "b0000000-0000-4000-8000-000000000102", "scope": "UNIVERSITY",
        "name": "Calculus II", "description": null, "courseCode": "MA264"}]
      """;

  private final CompleteOnboardingUseCase completeOnboarding;
  private final DeclareInterestsUseCase declareInterests;
  private final LearningInterestsQuery learningInterests;

  LearningInterestsController(
      CompleteOnboardingUseCase completeOnboarding,
      DeclareInterestsUseCase declareInterests,
      LearningInterestsQuery learningInterests) {
    this.completeOnboarding = completeOnboarding;
    this.declareInterests = declareInterests;
    this.learningInterests = learningInterests;
  }

  @PostMapping("/onboarding/interests")
  @Operation(
      summary = "Finishes the onboarding with what the student needs and can teach",
      description =
          """
          US40: the last step, after the profile and the academic record. Both lists are asked of \
          every student, whatever their term: either may be empty, but neither may be left out. \
          The needs are saved; each item the student can teach is offered through the academic \
          record, as POST /api/v1/tutor/skills does, and one their record does not back, or a \
          global tool, is skipped rather than refused. Then the onboarding step becomes COMPLETED, \
          which GET /api/v1/me shows.

          All in one transaction: a refusal saves nothing and leaves the step as it was. Sent \
          again once COMPLETED, it changes nothing that is already there and answers the same. \
          The courses the record suggests are in GET /api/v1/tutor/skills/suggestions.
          """)
  @ApiResponse(
      responseCode = "200",
      description = "The onboarding is complete: the saved needs and the skills enabled",
      content =
          @Content(
              schema = @Schema(implementation = OnboardingInterestsResponse.class),
              examples =
                  @ExampleObject(
                      value =
                          """
                          {"learning": [{"id": "b0000000-0000-4000-8000-000000000102",
                            "scope": "UNIVERSITY", "name": "Calculus II", "description": null,
                            "courseCode": "MA264"}],
                           "teaching": [{"id": "c0000000-0000-4000-8000-000000000001",
                            "catalogItemId": "b0000000-0000-4000-8000-000000000101",
                            "status": "ENABLED", "accreditationPath": "ACADEMIC_RECORD",
                            "accreditedGrade": 17.50, "enabledAt": "2026-10-20T15:00:00Z"}]}
                          """)))
  @ApiResponse(
      responseCode = "400",
      description = "learning or teaching is missing or null, has more than 50 items, or an item is not an identifier",
      content = @Content(schema = @Schema(implementation = ApiError.class)))
  @ApiResponse(
      responseCode = "404",
      description = "An item does not exist or belongs to another university",
      content = @Content(schema = @Schema(implementation = ApiError.class)))
  @ApiResponse(
      responseCode = "409",
      description = "The profile or the academic record is not done yet",
      content = @Content(schema = @Schema(implementation = ApiError.class)))
  OnboardingInterestsResponse finishOnboarding(
      @io.swagger.v3.oas.annotations.parameters.RequestBody(
              content =
                  @Content(
                      examples =
                          @ExampleObject(
                              value =
                                  """
                                  {"learning": ["b0000000-0000-4000-8000-000000000102"],
                                   "teaching": ["b0000000-0000-4000-8000-000000000101"]}
                                  """)))
          @Valid
          @RequestBody
          OnboardingInterestsRequest request) {
    UUID studentId = CurrentUser.require();
    return OnboardingInterestsResponse.of(
        completeOnboarding.execute(studentId, request.learning(), request.teaching()));
  }

  @GetMapping("/learning-interests")
  @Operation(
      summary = "What the student needs help with",
      description =
          """
          US41: the catalogue items the student said they need help with, sorted by name. The \
          whole set, since it is what the student edits and sends back with PUT.
          """)
  @ApiResponse(
      responseCode = "200",
      description = "The items, possibly none",
      content =
          @Content(
              array = @ArraySchema(schema = @Schema(implementation = CatalogItemResponse.class)),
              examples = @ExampleObject(value = INTERESTS_EXAMPLE)))
  List<CatalogItemResponse> learningInterests() {
    UUID studentId = CurrentUser.require();
    return learningInterests.of(studentId).stream().map(CatalogItemResponse::of).toList();
  }

  @PutMapping("/learning-interests")
  @Operation(
      summary = "Replaces what the student needs help with",
      description =
          """
          US41: the items sent become the whole set, at any moment after the onboarding: those \
          left out are dropped and an empty list leaves none. What the student teaches is not \
          touched (it is managed under /api/v1/tutor/skills), and neither is any booking already \
          confirmed. Answers the set as GET does.
          """)
  @ApiResponse(
      responseCode = "200",
      description = "The new set",
      content =
          @Content(
              array = @ArraySchema(schema = @Schema(implementation = CatalogItemResponse.class)),
              examples = @ExampleObject(value = INTERESTS_EXAMPLE)))
  @ApiResponse(
      responseCode = "400",
      description = "catalogItemIds is missing or null, has more than 50 items, or an item is not an identifier",
      content = @Content(schema = @Schema(implementation = ApiError.class)))
  @ApiResponse(
      responseCode = "404",
      description = "An item does not exist or belongs to another university; nothing changed",
      content = @Content(schema = @Schema(implementation = ApiError.class)))
  List<CatalogItemResponse> replaceLearningInterests(
      @io.swagger.v3.oas.annotations.parameters.RequestBody(
              content =
                  @Content(
                      examples =
                          @ExampleObject(
                              value = "{\"catalogItemIds\": [\"b0000000-0000-4000-8000-000000000102\"]}")))
          @Valid
          @RequestBody
          LearningInterestsRequest request) {
    UUID studentId = CurrentUser.require();
    declareInterests.replaceLearningInterests(studentId, request.catalogItemIds());
    return learningInterests.of(studentId).stream().map(CatalogItemResponse::of).toList();
  }
}
