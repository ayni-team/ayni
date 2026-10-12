package pe.ayni.audit.interfaces.rest;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import pe.ayni.audit.application.DismissAnomalyUseCase;
import pe.ayni.audit.application.ListAnomaliesUseCase;
import pe.ayni.audit.application.ReadAnomalyUseCase;
import pe.ayni.shared.tenancy.CurrentUser;

/** The alerts of anomalous usage that a coordinator reviews. */
@RestController
@RequestMapping("/api/v1/coordinator/anomalies")
@Tag(name = "Anomalies", description = "Patterns that suggest credits are being piled up artificially")
class AnomaliesController {

  private final ListAnomaliesUseCase list;
  private final ReadAnomalyUseCase read;
  private final DismissAnomalyUseCase dismiss;

  AnomaliesController(
      ListAnomaliesUseCase list, ReadAnomalyUseCase read, DismissAnomalyUseCase dismiss) {
    this.list = list;
    this.read = read;
    this.dismiss = dismiss;
  }

  @GetMapping
  @Operation(
      summary = "The alerts of anomalous usage of the university",
      description =
          """
          US47: the patterns the audit found that suggest credits are being piled up artificially: \
          a session that was not verified, one that lasted far less than booked, a tutor and a \
          student who meet more than is usual. The most recently detected first. Filter by \
          status; with no status every alert is listed.

          Only an active coordinator of the university may read them.
          """)
  @TenantAndUser
  @ApiResponse(
      responseCode = "200",
      description = "A page of alerts, possibly empty",
      content = @Content(schema = @Schema(implementation = AnomaliesResponse.class)))
  @ApiResponse(
      responseCode = "400",
      description = "A header is missing, or the status is not OPEN, DISMISSED or CONFIRMED",
      content = @Content(schema = @Schema(implementation = ApiError.class)))
  @ApiResponse(
      responseCode = "403",
      description = "The person is not an active coordinator of the university",
      content = @Content(schema = @Schema(implementation = ApiError.class)))
  AnomaliesResponse anomalies(
      @Parameter(description = "OPEN, DISMISSED or CONFIRMED") @RequestParam(required = false)
          String status,
      @Parameter(description = "Page, from zero") @RequestParam(defaultValue = "0") int page,
      @Parameter(description = "Alerts per page, at most 100") @RequestParam(defaultValue = "20")
          int size) {
    return AnomaliesResponse.of(list.execute(CurrentUser.require(), status, page, size));
  }

  @GetMapping("/{id}")
  @Operation(
      summary = "One alert with the pattern, the people and the sessions behind it",
      description =
          """
          US47: what raised the alert, who it involves and the concrete sessions that support it, \
          so a coordinator can open each one before deciding. An alert of another university does \
          not exist for the person asking.
          """)
  @TenantAndUser
  @ApiResponse(
      responseCode = "200",
      description = "The alert",
      content = @Content(schema = @Schema(implementation = AnomalyResponse.class)))
  @ApiResponse(
      responseCode = "400",
      description = "A header is missing, or the id is not a UUID",
      content = @Content(schema = @Schema(implementation = ApiError.class)))
  @ApiResponse(
      responseCode = "403",
      description = "The person is not an active coordinator of the university",
      content = @Content(schema = @Schema(implementation = ApiError.class)))
  @ApiResponse(
      responseCode = "404",
      description = "The alert does not exist in this university",
      content = @Content(schema = @Schema(implementation = ApiError.class)))
  AnomalyResponse anomaly(@PathVariable UUID id) {
    return AnomalyResponse.of(read.execute(CurrentUser.require(), id));
  }

  @PostMapping("/{id}/dismiss")
  @Operation(
      summary = "Dismiss an alert with a reason",
      description =
          """
          US47: the coordinator decides the pattern is not a problem. The alert is closed as \
          DISMISSED with who decided and when, the reason stays with it, and the decision is also \
          written in the activity log. An alert is decided once: dismissing one that was already \
          resolved is refused, also when two coordinators do it at the same moment.
          """)
  @TenantAndUser
  @ApiResponse(
      responseCode = "200",
      description = "The alert is dismissed",
      content = @Content(schema = @Schema(implementation = DismissalResponse.class)))
  @ApiResponse(
      responseCode = "400",
      description = "A header is missing, the id is not a UUID, or the reason is missing or too long",
      content = @Content(schema = @Schema(implementation = ApiError.class)))
  @ApiResponse(
      responseCode = "403",
      description = "The person is not an active coordinator of the university",
      content = @Content(schema = @Schema(implementation = ApiError.class)))
  @ApiResponse(
      responseCode = "404",
      description = "The alert does not exist in this university",
      content = @Content(schema = @Schema(implementation = ApiError.class)))
  @ApiResponse(
      responseCode = "409",
      description = "The alert was already resolved",
      content = @Content(schema = @Schema(implementation = ApiError.class)))
  DismissalResponse dismiss(@PathVariable UUID id, @RequestBody DismissAnomalyRequest request) {
    return DismissalResponse.of(dismiss.execute(CurrentUser.require(), id, request.reason()));
  }
}
