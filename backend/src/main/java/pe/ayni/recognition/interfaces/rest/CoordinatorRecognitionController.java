package pe.ayni.recognition.interfaces.rest;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import pe.ayni.recognition.application.RequestCaseQuery;
import pe.ayni.recognition.application.RequestQueueQuery;
import pe.ayni.recognition.application.ResolveRecognitionRequestUseCase;
import pe.ayni.recognition.application.SupportingSessionsQuery;
import pe.ayni.recognition.domain.model.RequestStatus;
import pe.ayni.shared.tenancy.CurrentUser;

/**
 * What a coordinator does with the recognition requests of their university: see what waits, review
 * a case with all its evidence and decide.
 *
 * <p>Every endpoint answers for the coordinator making the request, read from {@link CurrentUser},
 * and the use case checks that they are one.
 */
@RestController
@RequestMapping("/api/v1/coordinator/recognition/requests")
@Tag(name = "Coordinator recognition", description = "Reviewing and deciding recognition requests")
class CoordinatorRecognitionController {

  private final RequestQueueQuery queue;
  private final RequestCaseQuery requestCase;
  private final ResolveRecognitionRequestUseCase resolveRequest;
  private final SupportingSessionsQuery supportingSessions;

  CoordinatorRecognitionController(
      RequestQueueQuery queue,
      RequestCaseQuery requestCase,
      ResolveRecognitionRequestUseCase resolveRequest,
      SupportingSessionsQuery supportingSessions) {
    this.supportingSessions = supportingSessions;
    this.resolveRequest = resolveRequest;
    this.requestCase = requestCase;
    this.queue = queue;
  }

  @GetMapping
  @Operation(
      summary = "The recognition requests of the university",
      description =
          """
          US29: the requests nobody decided yet, with the student who sent them, the hours they \
          present and since when they wait, the one that waited longest first. The hours are the \
          ones the request was submitted with.

          With status, the requests in that state instead: the history of the decisions.
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
      description = "A page of requests",
      content = @Content(schema = @Schema(implementation = QueueResponse.class)))
  @ApiResponse(
      responseCode = "400",
      description = "The state is not one of SUBMITTED, UNDER_REVIEW, APPROVED or REJECTED",
      content = @Content(schema = @Schema(implementation = ApiError.class)))
  @ApiResponse(
      responseCode = "403",
      description = "The person asking is not a coordinator",
      content = @Content(schema = @Schema(implementation = ApiError.class)))
  QueueResponse queue(
      @Parameter(description = "Only the requests in this state; by default the ones that wait")
          @RequestParam(required = false)
          RequestStatus status,
      @Parameter(description = "Page, from zero") @RequestParam(defaultValue = "0") int page,
      @Parameter(description = "Requests per page, at most 100") @RequestParam(defaultValue = "20") int size) {
    UUID coordinatorId = CurrentUser.require();
    return QueueResponse.of(queue.of(coordinatorId, status, page, size));
  }

  @GetMapping("/{id}")
  @Operation(
      summary = "A recognition request with all its evidence",
      description =
          """
          US29: the case to review. The sessions that back the request, each with its date, how long \
          it really lasted, whether the presence of both participants was verified and the rating \
          the tutor received. The alerts of the audit about those sessions appear next to them, \
          and together in alerts.

          The figures and the sessions are the ones the request was submitted with, so reading it \
          days later shows the same: they do not change while the request is evaluated.
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
      description = "The case",
      content = @Content(schema = @Schema(implementation = CaseResponse.class)))
  @ApiResponse(
      responseCode = "403",
      description = "The person asking is not a coordinator",
      content = @Content(schema = @Schema(implementation = ApiError.class)))
  @ApiResponse(
      responseCode = "404",
      description = "The request does not exist in this university",
      content = @Content(schema = @Schema(implementation = ApiError.class)))
  CaseResponse requestCase(@Parameter(description = "Identifier of the request") @PathVariable UUID id) {
    UUID coordinatorId = CurrentUser.require();
    return CaseResponse.of(requestCase.of(coordinatorId, id));
  }

  @GetMapping("/{id}/sessions")
  @Operation(
      summary = "The tutoring sessions behind a recognition request",
      description =
          """
          US30: the list of sessions that support the request, each with its date, its duration and \
          the course or skill that was taught, so the decision rests on concrete evidence and not \
          only on a total.

          The hours of the sessions listed add up to the total the student presented (listedHours \
          and totalHours are the same): both were copied when the request was submitted.
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
      description = "The sessions",
      content = @Content(schema = @Schema(implementation = SupportResponse.class)))
  @ApiResponse(
      responseCode = "403",
      description = "The person asking is not a coordinator of the university",
      content = @Content(schema = @Schema(implementation = ApiError.class)))
  @ApiResponse(
      responseCode = "404",
      description = "The request does not exist in this university",
      content = @Content(schema = @Schema(implementation = ApiError.class)))
  SupportResponse support(@Parameter(description = "Identifier of the request") @PathVariable UUID id) {
    UUID coordinatorId = CurrentUser.require();
    return SupportResponse.of(supportingSessions.of(coordinatorId, id));
  }

  @PostMapping("/{id}/decision")
  @Operation(
      summary = "Approves or rejects a recognition request",
      description =
          """
          US29: the coordinator decides after reviewing the case, and always says why. The decision \
          is recorded with who took it, when and the reason, and the student reads it in their \
          requests. RecognitionResolved is published so the student is told.

          A request is decided once: deciding it again is a conflict, and when two coordinators \
          decide at the same moment only one of them succeeds. Ayni certifies nothing by itself: \
          this keeps what the university decided.
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
      content = @Content(schema = @Schema(implementation = DecisionResponse.class)))
  @ApiResponse(
      responseCode = "400",
      description = "The decision is neither APPROVE nor REJECT, or the reason is missing or too long",
      content = @Content(schema = @Schema(implementation = ApiError.class)))
  @ApiResponse(
      responseCode = "403",
      description = "The person asking is not a coordinator",
      content = @Content(schema = @Schema(implementation = ApiError.class)))
  @ApiResponse(
      responseCode = "404",
      description = "The request does not exist in this university",
      content = @Content(schema = @Schema(implementation = ApiError.class)))
  @ApiResponse(
      responseCode = "409",
      description = "The request was already decided",
      content = @Content(schema = @Schema(implementation = ApiError.class)))
  DecisionResponse decide(
      @Parameter(description = "Identifier of the request") @PathVariable UUID id,
      @Valid @RequestBody DecisionRequest body) {
    UUID coordinatorId = CurrentUser.require();
    return DecisionResponse.of(resolveRequest.execute(coordinatorId, id, body.decision(), body.reason()));
  }
}
