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
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import pe.ayni.shared.tenancy.CurrentUser;
import pe.ayni.skills.application.CatalogQuery;
import pe.ayni.skills.application.OfferApprovedCourseUseCase;
import pe.ayni.skills.application.SuggestedCoursesQuery;
import pe.ayni.skills.application.TutorSkillsQuery;
import pe.ayni.skills.application.WithdrawSkillUseCase;

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
@RequestMapping("/api/v1")
@Validated
@Tag(name = "Skills", description = "Catalogue, offered skills and their accreditation")
class SkillsController {

  private final CatalogQuery catalog;
  private final SuggestedCoursesQuery suggestions;
  private final TutorSkillsQuery tutorSkills;
  private final OfferApprovedCourseUseCase offerApprovedCourse;
  private final WithdrawSkillUseCase withdrawSkill;

  SkillsController(
      CatalogQuery catalog,
      SuggestedCoursesQuery suggestions,
      TutorSkillsQuery tutorSkills,
      OfferApprovedCourseUseCase offerApprovedCourse,
      WithdrawSkillUseCase withdrawSkill) {
    this.catalog = catalog;
    this.suggestions = suggestions;
    this.tutorSkills = tutorSkills;
    this.offerApprovedCourse = offerApprovedCourse;
    this.withdrawSkill = withdrawSkill;
  }

  @GetMapping("/catalog")
  @Operation(
      summary = "The catalogue visible to the current university",
      description =
          """
          Every active item a student of this university may see: the global tools that ship with \
          Ayni, plus the courses that belong to their own university. Sorted by name, and \
          narrowed by category or by part of the name when asked.
          """)
  @Parameter(
      in = ParameterIn.HEADER,
      name = "X-Tenant-Id",
      required = true,
      description = "University the request belongs to. Read by TenantFilter",
      schema = @Schema(type = "string", example = "UPC"))
  @ApiResponse(
      responseCode = "200",
      description = "One page of the visible catalogue",
      content = @Content(schema = @Schema(implementation = CatalogPage.class)))
  CatalogPage catalog(
      @Parameter(description = "Keep only items of this category") @RequestParam(required = false)
          UUID category,
      @Parameter(description = "Part of the name, ignoring case", example = "python")
          @RequestParam(required = false)
          @Size(max = 80)
          String q,
      @Parameter(description = "Page number, starting at zero") @RequestParam(defaultValue = "0")
          @Min(0)
          int page,
      @Parameter(description = "Items per page") @RequestParam(defaultValue = "20") @Min(1)
          @Max(100)
          int size) {
    return CatalogPage.of(catalog.visibleItems(category, q, page, size));
  }

  @GetMapping("/tutor/skills/suggestions")
  @Operation(
      summary = "Courses the tutor could offer without searching for them",
      description =
          """
          US13, scenario 2: every course the tutor's academic record already clears and does not \
          offer yet. A course never approved, or one the tutor offers right now, does not appear \
          here; one the tutor withdrew does, because it can be offered again.
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

  @PostMapping("/tutor/skills")
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
  @ApiResponse(
      responseCode = "409",
      description = "The tutor already offers this item",
      content = @Content(schema = @Schema(implementation = ApiError.class)))
  OfferedSkillResponse offer(@Valid @RequestBody OfferSkillRequest request) {
    UUID tutorId = CurrentUser.require();
    return OfferedSkillResponse.of(offerApprovedCourse.execute(tutorId, request.catalogItemId()));
  }

  @GetMapping("/tutor/skills")
  @Operation(
      summary = "The skills the tutor offers, each with where it stands",
      description =
          """
          US18, scenario 1: every skill the tutor offers or ever offered, with its status, the \
          path it was accredited through and the date its status last changed. Withdrawn skills \
          are listed too, so the tutor can offer one again.
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
      description = "The tutor's skills, sorted by name",
      content = @Content(array = @ArraySchema(schema = @Schema(implementation = TutorSkillResponse.class))))
  List<TutorSkillResponse> mySkills() {
    UUID tutorId = CurrentUser.require();
    return tutorSkills.of(tutorId).stream().map(TutorSkillResponse::of).toList();
  }

  @DeleteMapping("/tutor/skills/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @Operation(
      summary = "Stops offering a skill",
      description =
          """
          US18, scenario 5: the skill stops appearing in the search. Bookings already confirmed \
          for it stand. The skill stays in the tutor's list as withdrawn and can be offered again.
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
  @ApiResponse(responseCode = "204", description = "The skill was withdrawn")
  @ApiResponse(
      responseCode = "403",
      description = "The skill belongs to another tutor",
      content = @Content(schema = @Schema(implementation = ApiError.class)))
  @ApiResponse(
      responseCode = "404",
      description = "No skill has that identifier in this university",
      content = @Content(schema = @Schema(implementation = ApiError.class)))
  @ApiResponse(
      responseCode = "409",
      description = "The skill is not enabled, so there is nothing to withdraw",
      content = @Content(schema = @Schema(implementation = ApiError.class)))
  void withdraw(@PathVariable UUID id) {
    UUID tutorId = CurrentUser.require();
    withdrawSkill.execute(tutorId, id);
  }
}
