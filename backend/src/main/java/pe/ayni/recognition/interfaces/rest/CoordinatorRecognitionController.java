package pe.ayni.recognition.interfaces.rest;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import pe.ayni.recognition.application.RequestQueueQuery;
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

  CoordinatorRecognitionController(RequestQueueQuery queue) {
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
}
