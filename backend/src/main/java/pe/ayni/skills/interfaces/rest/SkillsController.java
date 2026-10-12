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
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
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
import org.springframework.web.multipart.MultipartFile;
import pe.ayni.shared.tenancy.CurrentUser;
import pe.ayni.skills.application.CatalogQuery;
import pe.ayni.skills.application.MyProposalsQuery;
import pe.ayni.skills.application.OfferApprovedCourseUseCase;
import pe.ayni.skills.application.ProposeSkillUseCase;
import pe.ayni.skills.application.SimilarItemsQuery;
import pe.ayni.skills.application.SubmitEvidenceUseCase;
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
  private final SimilarItemsQuery similarItems;
  private final SuggestedCoursesQuery suggestions;
  private final TutorSkillsQuery tutorSkills;
  private final OfferApprovedCourseUseCase offerApprovedCourse;
  private final WithdrawSkillUseCase withdrawSkill;
  private final SubmitEvidenceUseCase submitEvidenceUseCase;
  private final ProposeSkillUseCase proposeSkill;
  private final MyProposalsQuery myProposals;

  SkillsController(
      CatalogQuery catalog,
      SimilarItemsQuery similarItems,
      SuggestedCoursesQuery suggestions,
      TutorSkillsQuery tutorSkills,
      OfferApprovedCourseUseCase offerApprovedCourse,
      WithdrawSkillUseCase withdrawSkill,
      SubmitEvidenceUseCase submitEvidenceUseCase,
      ProposeSkillUseCase proposeSkill,
      MyProposalsQuery myProposals) {
    this.catalog = catalog;
    this.similarItems = similarItems;
    this.suggestions = suggestions;
    this.tutorSkills = tutorSkills;
    this.offerApprovedCourse = offerApprovedCourse;
    this.withdrawSkill = withdrawSkill;
    this.submitEvidenceUseCase = submitEvidenceUseCase;
    this.proposeSkill = proposeSkill;
    this.myProposals = myProposals;
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

  @GetMapping("/catalog/similar")
  @Operation(
      summary = "Catalogue items that look like a name",
      description =
          """
          The items of the catalogue that look like the name a student is about to propose, the \
          closest first and at most five. It forgives a typo, accents, case and punctuation, and \
          finds a name inside another, so "NodeJS" shows "Node.js". It compares against every \
          active item the student's university can see. Empty when nothing looks like it.
          """)
  @Parameter(
      in = ParameterIn.HEADER,
      name = "X-Tenant-Id",
      required = true,
      description = "University the request belongs to. Read by TenantFilter",
      schema = @Schema(type = "string", example = "UPC"))
  @ApiResponse(
      responseCode = "200",
      description = "The items that look like the name, possibly none",
      content =
          @Content(array = @ArraySchema(schema = @Schema(implementation = CatalogItemResponse.class))))
  @ApiResponse(
      responseCode = "400",
      description = "The name is missing, blank or longer than 160 characters",
      content = @Content(schema = @Schema(implementation = ApiError.class)))
  List<CatalogItemResponse> similarItems(
      @Parameter(description = "What the student would call the skill", example = "NodeJS")
          @RequestParam
          @NotBlank
          @Size(max = 160)
          String name) {
    return similarItems.similarTo(name).stream().map(CatalogItemResponse::of).toList();
  }

  @PostMapping("/tutor/skills/proposals")
  @ResponseStatus(HttpStatus.CREATED)
  @Operation(
      summary = "Proposes a tool the catalogue does not have",
      description =
          """
          US42: a student who masters a tool that is not in the catalogue proposes it with a name, \
          a category and a short description. It waits for a moderator to resolve it.

          Before it is recorded, the catalogue is compared with the name. If skills that look like \
          it exist, the answer is 409 with the list, and the student sends the same request again \
          with confirmDistinct set to true when theirs is a different one. A name the catalogue \
          already has, or a proposal the student already has waiting, is refused whatever they \
          confirm.
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
      description = "Student making the request. Read by CurrentUserFilter",
      schema =
          @Schema(type = "string", format = "uuid", example = "11111111-1111-4111-8111-111111111111"))
  @ApiResponse(
      responseCode = "201",
      description = "The proposal, waiting for a moderator",
      content = @Content(schema = @Schema(implementation = SkillProposalResponse.class)))
  @ApiResponse(
      responseCode = "400",
      description = "The name has fewer than three characters or too many, or the description is too long",
      content = @Content(schema = @Schema(implementation = ApiError.class)))
  @ApiResponse(
      responseCode = "404",
      description = "The category does not exist",
      content = @Content(schema = @Schema(implementation = ApiError.class)))
  @ApiResponse(
      responseCode = "409",
      description =
          "Skills that look like it exist and it was not confirmed (with the list), the catalogue has"
              + " that name, or the student already has it waiting",
      content = @Content(schema = @Schema(implementation = SimilarSkillsError.class)))
  SkillProposalResponse propose(@Valid @RequestBody ProposeSkillRequest request) {
    UUID proposerId = CurrentUser.require();
    return SkillProposalResponse.of(
        proposeSkill.execute(
            proposerId,
            request.categoryId(),
            request.name(),
            request.description(),
            request.confirmDistinct()));
  }

  @GetMapping("/tutor/skills/proposals")
  @Operation(
      summary = "The tools the student proposed and where each stands",
      description =
          """
          US42, scenario 3: every proposal of the student, newest first, with its status and, once \
          a moderator resolved it, the decision and the reason. An approved one carries the \
          catalogue item it created, which the student can then offer.
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
      description = "Student making the request. Read by CurrentUserFilter",
      schema =
          @Schema(type = "string", format = "uuid", example = "11111111-1111-4111-8111-111111111111"))
  @ApiResponse(
      responseCode = "200",
      description = "The student's proposals, possibly none",
      content =
          @Content(array = @ArraySchema(schema = @Schema(implementation = SkillProposalResponse.class))))
  List<SkillProposalResponse> proposals() {
    UUID proposerId = CurrentUser.require();
    return myProposals.of(proposerId).stream().map(SkillProposalResponse::of).toList();
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

          Only university courses are enabled here. A global tool has no academic record to check \
          against, and is refused with a message pointing at reviewed evidence instead, with one \
          exception: a tool the tutor withdrew after a coordinator accepted their evidence is \
          enabled again without a new review.
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

  @PostMapping(path = "/tutor/skills/{id}/validation", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  @ResponseStatus(HttpStatus.CREATED)
  @Operation(
      summary = "Presents evidence for a global tool",
      description =
          """
          US16: a tutor who learned a tool by working attaches a portfolio or a certificate, and \
          it waits for a coordinator of their university. The identifier in the path is the one of \
          the catalogue item, because the skill does not exist until the first submission.

          Between one and three files, each a PDF, a PNG or a JPEG of at most 5 MB. The first \
          submission creates the skill as pending. After a rejection the same call puts it back \
          in the queue with the new files, and the earlier submission stays as history. Courses \
          are refused: they are enabled by the academic record.
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
      description = "The evidence was received and waits for a review",
      content = @Content(schema = @Schema(implementation = EvidenceSubmissionResponse.class)))
  @ApiResponse(
      responseCode = "400",
      description = "No files, too many, too big, not a PDF or an image, or the item is a course",
      content = @Content(schema = @Schema(implementation = ApiError.class)))
  @ApiResponse(
      responseCode = "404",
      description = "The catalogue item does not exist, or is not visible to this university",
      content = @Content(schema = @Schema(implementation = ApiError.class)))
  @ApiResponse(
      responseCode = "409",
      description = "The evidence already waits for a review, or the tool is already enabled",
      content = @Content(schema = @Schema(implementation = ApiError.class)))
  EvidenceSubmissionResponse submitEvidence(
      @Parameter(description = "Identifier of the catalogue item, a global tool") @PathVariable UUID id,
      @Parameter(description = "Anything the reviewer should know, at most 1000 characters")
          @RequestParam(required = false)
          String note,
      @Parameter(description = "The portfolio or certificate: one to three PDF, PNG or JPEG files")
          @RequestParam("files")
          List<MultipartFile> files)
      throws IOException {
    UUID tutorId = CurrentUser.require();

    List<InputStream> opened = new ArrayList<>(files.size());
    try {
      List<SubmitEvidenceUseCase.EvidenceFileInput> inputs = new ArrayList<>(files.size());
      for (MultipartFile file : files) {
        InputStream stream = file.getInputStream();
        opened.add(stream);
        inputs.add(
            new SubmitEvidenceUseCase.EvidenceFileInput(
                plainName(file.getOriginalFilename()), file.getContentType(), file.getSize(), stream));
      }
      return EvidenceSubmissionResponse.of(submitEvidenceUseCase.execute(tutorId, id, note, inputs));
    } finally {
      for (InputStream stream : opened) {
        try {
          stream.close();
        } catch (IOException ignored) {
          // Nothing left to read from it, and the answer is already decided.
        }
      }
    }
  }

  /** The name without the folders some browsers send along with it. */
  private static String plainName(String original) {
    if (original == null) {
      return "";
    }
    return original.substring(Math.max(original.lastIndexOf('/'), original.lastIndexOf('\\')) + 1);
  }
}
