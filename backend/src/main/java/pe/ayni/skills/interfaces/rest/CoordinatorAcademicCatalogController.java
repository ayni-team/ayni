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
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pe.ayni.shared.tenancy.CurrentUser;
import pe.ayni.skills.application.AcademicCoursesQuery;
import pe.ayni.skills.application.LoadAcademicCatalogUseCase;
import pe.ayni.skills.application.MinimumGradeQuery;
import pe.ayni.skills.application.RetireCourseUseCase;
import pe.ayni.skills.application.SetMinimumGradeUseCase;

/**
 * What a coordinator configures about the courses of their university: which ones there are and the
 * grade that enables a student to teach them.
 *
 * <p>Every endpoint answers for the coordinator making the request, read from {@link CurrentUser},
 * and the use case checks that they are one.
 */
@RestController
@RequestMapping("/api/v1/coordinator/academic-catalog")
@Validated
@Tag(
    name = "Coordinator academic catalogue",
    description = "The courses of the university and the grade to teach them")
class CoordinatorAcademicCatalogController {

  private final AcademicCoursesQuery courses;
  private final LoadAcademicCatalogUseCase loadCourses;
  private final MinimumGradeQuery minimumGrade;
  private final RetireCourseUseCase retireCourse;
  private final SetMinimumGradeUseCase setMinimumGrade;

  CoordinatorAcademicCatalogController(
      AcademicCoursesQuery courses,
      LoadAcademicCatalogUseCase loadCourses,
      MinimumGradeQuery minimumGrade,
      RetireCourseUseCase retireCourse,
      SetMinimumGradeUseCase setMinimumGrade) {
    this.courses = courses;
    this.loadCourses = loadCourses;
    this.minimumGrade = minimumGrade;
    this.retireCourse = retireCourse;
    this.setMinimumGrade = setMinimumGrade;
  }

  @GetMapping("/courses")
  @Operation(
      summary = "The courses of the university",
      description =
          """
          US51: the courses the university has loaded, by course code, with the retired ones marked \
          as such.
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
      description = "Coordinator making the request. Read by CurrentUserFilter",
      schema =
          @Schema(type = "string", format = "uuid", example = "22222222-2222-4222-8222-222222222222"))
  @ApiResponse(
      responseCode = "200",
      description = "The courses",
      content =
          @Content(array = @ArraySchema(schema = @Schema(implementation = AcademicCourseResponse.class))))
  @ApiResponse(
      responseCode = "403",
      description = "The person asking is not a coordinator",
      content = @Content(schema = @Schema(implementation = ApiError.class)))
  List<AcademicCourseResponse> courses() {
    UUID coordinatorId = CurrentUser.require();
    return courses.of(coordinatorId).stream().map(AcademicCourseResponse::of).toList();
  }

  @PostMapping("/courses")
  @Operation(
      summary = "Loads the courses of the curriculum",
      description =
          """
          US51: the courses become available for the students of the university to search for and \
          to offer. Loading adds and updates: a course already there takes the name and description \
          of the list, a retired one is brought back, and one missing from the list is left as it \
          was. To take a course out, retire it.

          All or nothing: a list with a blank code or name, or a code twice, saves nothing. The code \
          is what the academic system reports on an approved course.
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
      description = "Coordinator making the request. Read by CurrentUserFilter",
      schema =
          @Schema(type = "string", format = "uuid", example = "22222222-2222-4222-8222-222222222222"))
  @ApiResponse(
      responseCode = "200",
      description = "The courses are loaded, each with what happened to it",
      content = @Content(schema = @Schema(implementation = LoadCoursesResponse.class)))
  @ApiResponse(
      responseCode = "400",
      description =
          "The list is empty or too long, a code or a name is missing or too long, a code is"
              + " repeated, or the body cannot be read",
      content = @Content(schema = @Schema(implementation = ApiError.class)))
  @ApiResponse(
      responseCode = "403",
      description = "The person asking is not a coordinator",
      content = @Content(schema = @Schema(implementation = ApiError.class)))
  LoadCoursesResponse loadCourses(@Valid @RequestBody LoadCoursesRequest request) {
    UUID coordinatorId = CurrentUser.require();
    return LoadCoursesResponse.of(loadCourses.execute(coordinatorId, request.toCourses()));
  }

  @PostMapping("/courses/{id}/retire")
  @Operation(
      summary = "Takes a course out of the curriculum",
      description =
          """
          US51: the course can no longer be offered and the search stops finding it. The tutors who \
          offered it have the offer withdrawn, and the sessions already booked stand.

          The coordinator first reads how many tutors are affected in the usage of the course \
          (GET /api/v1/coordinator/catalog/{id}/usage) and confirms by sending that number as \
          confirmedTutors. If it is no longer the number of tutors who offer the course, nothing is \
          retired and the conflict says the number as it is now.

          Only the courses of the university: a global tool is not found here.
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
      description = "Coordinator making the request. Read by CurrentUserFilter",
      schema =
          @Schema(type = "string", format = "uuid", example = "22222222-2222-4222-8222-222222222222"))
  @ApiResponse(
      responseCode = "200",
      description = "The course was retired",
      content = @Content(schema = @Schema(implementation = RetirementResponse.class)))
  @ApiResponse(
      responseCode = "400",
      description = "The confirmation is missing or negative, or the body cannot be read",
      content = @Content(schema = @Schema(implementation = ApiError.class)))
  @ApiResponse(
      responseCode = "403",
      description = "The person asking is not a coordinator",
      content = @Content(schema = @Schema(implementation = ApiError.class)))
  @ApiResponse(
      responseCode = "404",
      description = "It is not a course of this university",
      content = @Content(schema = @Schema(implementation = ApiError.class)))
  @ApiResponse(
      responseCode = "409",
      description = "The course is already retired, or the number of tutors changed since it was read",
      content = @Content(schema = @Schema(implementation = ApiError.class)))
  RetirementResponse retireCourse(
      @Parameter(description = "Identifier of the course") @PathVariable UUID id,
      @Valid @RequestBody RetireCatalogItemRequest request) {
    UUID coordinatorId = CurrentUser.require();
    return RetirementResponse.of(retireCourse.execute(coordinatorId, id, request.confirmedTutors()));
  }

  @GetMapping("/minimum-grade")
  @Operation(
      summary = "The minimum grade needed to teach a course",
      description =
          """
          US51: the grade a student needs in a course to teach it. While the coordinator has not \
          set one it is the grade the university was registered with, and updatedAt is absent.
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
      description = "Coordinator making the request. Read by CurrentUserFilter",
      schema =
          @Schema(type = "string", format = "uuid", example = "22222222-2222-4222-8222-222222222222"))
  @ApiResponse(
      responseCode = "200",
      description = "The grade in force",
      content = @Content(schema = @Schema(implementation = MinimumGradeResponse.class)))
  @ApiResponse(
      responseCode = "403",
      description = "The person asking is not a coordinator",
      content = @Content(schema = @Schema(implementation = ApiError.class)))
  MinimumGradeResponse currentMinimumGrade() {
    UUID coordinatorId = CurrentUser.require();
    return MinimumGradeResponse.of(minimumGrade.current(coordinatorId));
  }

  @PutMapping("/minimum-grade")
  @Operation(
      summary = "Sets the minimum grade needed to teach a course",
      description =
          """
          US51: when a student offers a course, it is enabled on its own only if their grade \
          reaches this value. Changing it applies to the offers made from then on: the ones already \
          granted are kept as they were.

          The grade is on the scale of 0 to 20, with at most two decimals.
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
      description = "Coordinator making the request. Read by CurrentUserFilter",
      schema =
          @Schema(type = "string", format = "uuid", example = "22222222-2222-4222-8222-222222222222"))
  @ApiResponse(
      responseCode = "200",
      description = "The grade now in force",
      content = @Content(schema = @Schema(implementation = MinimumGradeResponse.class)))
  @ApiResponse(
      responseCode = "400",
      description = "The grade is missing, outside the scale, or the body cannot be read",
      content = @Content(schema = @Schema(implementation = ApiError.class)))
  @ApiResponse(
      responseCode = "403",
      description = "The person asking is not a coordinator",
      content = @Content(schema = @Schema(implementation = ApiError.class)))
  MinimumGradeResponse changeMinimumGrade(@Valid @RequestBody MinimumGradeRequest request) {
    UUID coordinatorId = CurrentUser.require();
    return MinimumGradeResponse.of(setMinimumGrade.execute(coordinatorId, request.minimumGrade()));
  }
}
