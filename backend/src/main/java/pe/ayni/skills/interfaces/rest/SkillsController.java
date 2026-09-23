package pe.ayni.skills.interfaces.rest;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import pe.ayni.shared.tenancy.CurrentUser;
import pe.ayni.skills.application.CatalogQuery;
import pe.ayni.skills.application.OfferApprovedCourseUseCase;
import pe.ayni.skills.application.SuggestedCoursesQuery;

/**
 * The catalogue, and offering a course from it.
 *
 * <p>The controller does exactly two things: it reads the request and it calls a use case. It never
 * touches an entity or a repository, and it holds no transaction, so the rules stay where they can
 * be tested without HTTP.
 *
 * <p>Every endpoint answers about the tutor making the request, read from {@link CurrentUser}, and
 * none takes a tutor as a parameter: until sign in exists, an endpoint that could name another
 * tutor could be pointed at one.
 */
@RestController
@RequestMapping("/api/v1/skills")
@Validated
@Tag(name = "Skills", description = "Catalogue, offered skills and their accreditation")
class SkillsController {

  private final CatalogQuery catalog;
  private final SuggestedCoursesQuery suggestions;
  private final OfferApprovedCourseUseCase offerApprovedCourse;

  SkillsController(
      CatalogQuery catalog,
      SuggestedCoursesQuery suggestions,
      OfferApprovedCourseUseCase offerApprovedCourse) {
    this.catalog = catalog;
    this.suggestions = suggestions;
    this.offerApprovedCourse = offerApprovedCourse;
  }

  @GetMapping("/catalog")
  @Operation(
      summary = "The catalogue visible to the current university",
      description =
          """
          Every active item a student of this university may see: the global tools that ship with \
          Ayni, plus the courses that belong to their own university.
          """)
  @Parameter(
      in = ParameterIn.HEADER,
      name = "X-Tenant-Id",
      required = true,
      description = "University the request belongs to. Read by TenantFilter",
      schema = @Schema(type = "string", example = "UPC"))
  @ApiResponse(
      responseCode = "200",
      description = "The visible catalogue",
      content = @Content(array = @ArraySchema(schema = @Schema(implementation = CatalogItemResponse.class))))
  List<CatalogItemResponse> catalog() {
    return catalog.visibleItems().stream().map(CatalogItemResponse::of).toList();
  }

  @GetMapping("/offers/suggestions")
  @Operation(
      summary = "Courses the tutor could offer without searching for them",
      description =
          """
          US13, scenario 2: every course the tutor's academic record already clears and does not \
          offer yet. A course never approved, or one already offered (even withdrawn), does not \
          appear here.
          """)
  @Parameter(
      in = ParameterIn.HEADER,
      name = "X-Tenant-Id",
      required = true,
      description = "University the request belongs to. Read by TenantFilter",
      schema = @Schema(type = "string", example = "UPC"))
  @Parameter(
      in = ParameterIn.HEADER,
      name = "X-User-Id",
      required = true,
      description = "Tutor making the request. Read by CurrentUserFilter",
      schema =
          @Schema(type = "string", format = "uuid", example = "11111111-1111-4111-8111-111111111111"))
  @ApiResponse(
      responseCode = "200",
      description = "The suggested courses",
      content =
          @Content(array = @ArraySchema(schema = @Schema(implementation = SuggestedCourseResponse.class))))
  List<SuggestedCourseResponse> suggestions() {
    UUID tutorId = CurrentUser.require();
    return suggestions.forTutor(tutorId).stream().map(SuggestedCourseResponse::of).toList();
  }

  @PostMapping("/offers")
  @ResponseStatus(HttpStatus.CREATED)
  @Operation(
      summary = "Offers a university course the tutor already passed",
      description =
          """
          US13: enabled the moment the grade the academic system reports clears the university's \
          threshold, with no further steps.

          Only university courses go through here. A global tool has no academic record to check \
          against, and is refused with a message pointing at reviewed evidence instead.
          """)
  @Parameter(
      in = ParameterIn.HEADER,
      name = "X-Tenant-Id",
      required = true,
      description = "University the request belongs to. Read by TenantFilter",
      schema = @Schema(type = "string", example = "UPC"))
  @Parameter(
      in = ParameterIn.HEADER,
      name = "X-User-Id",
      required = true,
      description = "Tutor making the request. Read by CurrentUserFilter",
      schema =
          @Schema(type = "string", format = "uuid", example = "11111111-1111-4111-8111-111111111111"))
  @ApiResponse(
      responseCode = "201",
      description = "The skill is enabled",
      content = @Content(schema = @Schema(implementation = OfferedSkillResponse.class)))
  @ApiResponse(
      responseCode = "400",
      description = "The item is global, the course is not approved, or the grade is too low",
      content = @Content(schema = @Schema(implementation = ApiError.class)))
  @ApiResponse(
      responseCode = "404",
      description = "The catalogue item does not exist, or is not visible to this university",
      content = @Content(schema = @Schema(implementation = ApiError.class)))
  OfferedSkillResponse offer(@Valid @RequestBody OfferSkillRequest request) {
    UUID tutorId = CurrentUser.require();
    return OfferedSkillResponse.of(offerApprovedCourse.execute(tutorId, request.catalogItemId()));
  }
}
