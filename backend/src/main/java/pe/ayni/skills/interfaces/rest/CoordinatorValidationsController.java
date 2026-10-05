package pe.ayni.skills.interfaces.rest;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import pe.ayni.shared.tenancy.CurrentUser;
import pe.ayni.skills.application.OpenEvidenceFileUseCase;
import pe.ayni.skills.application.PendingValidationsQuery;
import pe.ayni.skills.application.ResolveValidationUseCase;

/**
 * What a coordinator does with the evidence of their university: see the queue, open the files,
 * decide.
 *
 * <p>Every endpoint answers for the coordinator making the request, read from {@link CurrentUser},
 * and the use case checks that they are one: until the platform closes this route by role, that
 * check is the only thing between a student and the queue.
 */
@RestController
@RequestMapping("/api/v1/coordinator/validations")
@Validated
@Tag(name = "Coordinator validations", description = "Review of the evidence for global tools")
class CoordinatorValidationsController {

  private final PendingValidationsQuery pending;
  private final ResolveValidationUseCase resolveValidation;
  private final OpenEvidenceFileUseCase openEvidenceFile;

  CoordinatorValidationsController(
      PendingValidationsQuery pending,
      ResolveValidationUseCase resolveValidation,
      OpenEvidenceFileUseCase openEvidenceFile) {
    this.pending = pending;
    this.resolveValidation = resolveValidation;
    this.openEvidenceFile = openEvidenceFile;
  }

  @GetMapping
  @Operation(
      summary = "The submissions of evidence waiting for a decision",
      description =
          """
          US16: the pending queue of the coordinator's university, oldest first. Each item says who \
          submitted it, for which tool, what they wrote and which files came with it.
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
      description = "One page of the queue",
      content = @Content(schema = @Schema(implementation = PendingValidationsPage.class)))
  @ApiResponse(
      responseCode = "403",
      description = "The person asking is not a coordinator",
      content = @Content(schema = @Schema(implementation = ApiError.class)))
  PendingValidationsPage queue(
      @Parameter(description = "Page number, starting at zero") @RequestParam(defaultValue = "0")
          @Min(0)
          int page,
      @Parameter(description = "Submissions per page") @RequestParam(defaultValue = "20") @Min(1)
          @Max(100)
          int size) {
    UUID coordinatorId = CurrentUser.require();
    return PendingValidationsPage.of(pending.page(coordinatorId, page, size));
  }

  @PostMapping("/{id}/decision")
  @Operation(
      summary = "Approves or rejects a submission",
      description =
          """
          US16: approving enables the tool for the tutor by the reviewed evidence, and the request \
          keeps who decided and when. Rejecting needs a reason, because the student reads it and \
          submits again from there. A submission is decided once.
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
      description = "The decision was recorded",
      content = @Content(schema = @Schema(implementation = ValidationDecisionResponse.class)))
  @ApiResponse(
      responseCode = "400",
      description = "A rejection without a reason, or a body that cannot be read",
      content = @Content(schema = @Schema(implementation = ApiError.class)))
  @ApiResponse(
      responseCode = "403",
      description = "The person asking is not a coordinator",
      content = @Content(schema = @Schema(implementation = ApiError.class)))
  @ApiResponse(
      responseCode = "404",
      description = "No submission has that identifier in this university",
      content = @Content(schema = @Schema(implementation = ApiError.class)))
  @ApiResponse(
      responseCode = "409",
      description = "The submission was already decided",
      content = @Content(schema = @Schema(implementation = ApiError.class)))
  ValidationDecisionResponse decide(
      @PathVariable UUID id, @Valid @RequestBody ValidationDecisionRequest request) {
    UUID coordinatorId = CurrentUser.require();
    boolean approved = request.decision() == ValidationDecisionRequest.Decision.APPROVE;
    return ValidationDecisionResponse.of(
        resolveValidation.execute(coordinatorId, id, approved, request.reason()));
  }

  @GetMapping("/{id}/files/{fileId}")
  @Operation(
      summary = "Opens a file attached to a submission",
      description =
          """
          The coordinator reads the evidence here. The file always comes as a download, never to \
          be shown by the browser, since it was uploaded by a student. The path needs both the \
          submission and the file: the identifier of a file alone opens nothing.
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
  @ApiResponse(responseCode = "200", description = "The content of the file")
  @ApiResponse(
      responseCode = "403",
      description = "The person asking is not a coordinator",
      content = @Content(schema = @Schema(implementation = ApiError.class)))
  @ApiResponse(
      responseCode = "404",
      description = "That file is not one of that submission in this university",
      content = @Content(schema = @Schema(implementation = ApiError.class)))
  ResponseEntity<Resource> file(@PathVariable UUID id, @PathVariable UUID fileId) {
    UUID coordinatorId = CurrentUser.require();
    OpenEvidenceFileUseCase.OpenedEvidence opened = openEvidenceFile.execute(coordinatorId, id, fileId);

    return ResponseEntity.ok()
        .contentType(MediaType.parseMediaType(opened.file().getContentType()))
        .contentLength(opened.file().getSizeBytes())
        .header(
            HttpHeaders.CONTENT_DISPOSITION,
            ContentDisposition.attachment()
                .filename(opened.file().getFileName(), StandardCharsets.UTF_8)
                .build()
                .toString())
        .header("X-Content-Type-Options", "nosniff")
        .body(new InputStreamResource(opened.content()));
  }
}
