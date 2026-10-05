package pe.ayni.skills.interfaces.rest;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pe.ayni.shared.tenancy.CurrentUser;
import pe.ayni.skills.application.MinimumGradeQuery;
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
@Tag(name = "Coordinator academic catalogue", description = "The courses of the university and the grade to teach them")
class CoordinatorAcademicCatalogController {

  private final MinimumGradeQuery minimumGrade;
  private final SetMinimumGradeUseCase setMinimumGrade;

  CoordinatorAcademicCatalogController(
      MinimumGradeQuery minimumGrade, SetMinimumGradeUseCase setMinimumGrade) {
    this.minimumGrade = minimumGrade;
    this.setMinimumGrade = setMinimumGrade;
  }

  @GetMapping("/minimum-grade")
  @Operation(
      summary = "The minimum grade needed to teach a course",
      description =
          """
          US51: the grade a student needs in a course to teach it. While the coordinator has not set \
          one it is the grade the university was registered with, and updatedAt is absent.
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
      schema = @Schema(type = "string", format = "uuid", example = "22222222-2222-4222-8222-222222222222"))
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
          US51: when a student offers a course, it is enabled on its own only if their grade reaches \
          this value. Changing it applies to the offers made from then on: the ones already granted \
          are kept as they were.

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
      schema = @Schema(type = "string", format = "uuid", example = "22222222-2222-4222-8222-222222222222"))
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
