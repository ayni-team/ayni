package pe.ayni.analytics.interfaces.rest;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import pe.ayni.analytics.application.ConsultIndicatorsUseCase;
import pe.ayni.analytics.domain.model.UsageIndicators;
import pe.ayni.shared.tenancy.CurrentUser;

/** What a coordinator reads of how much their university uses the service. */
@RestController
@RequestMapping("/api/v1/coordinator/indicators")
@Tag(name = "Usage indicators", description = "How much the service is used, never by whom")
class IndicatorsController {

  private final ConsultIndicatorsUseCase consult;

  IndicatorsController(ConsultIndicatorsUseCase consult) {
    this.consult = consult;
  }

  @GetMapping
  @Operation(
      summary = "Usage of the university in a period",
      description =
          """
          US53: the sessions completed, the hours exchanged, the students who took part, the \
          students who joined and the recognitions granted in a period, as totals and as a series \
          with one entry per day. The days are those of the university's time zone, both ends are \
          included and a period covers at most 366 days.

          The active students of the totals are distinct people over the whole period, not the sum \
          of the days. Only figures of the university of the request are returned, and never the \
          name or the identifier of a person.
          """)
  @TenantAndUser
  @ApiResponse(
      responseCode = "200",
      description = "The totals and the series",
      content = @Content(schema = @Schema(implementation = IndicatorsResponse.class)))
  @ApiResponse(
      responseCode = "400",
      description =
          "A header or a date is missing, a date is not ISO 8601, the period ends before it starts"
              + " or covers more than 366 days",
      content = @Content(schema = @Schema(implementation = ApiError.class)))
  @ApiResponse(
      responseCode = "403",
      description = "The person asking is not an active coordinator of the university",
      content = @Content(schema = @Schema(implementation = ApiError.class)))
  IndicatorsResponse indicators(
      @Parameter(description = "First day of the period, included. ISO 8601", example = "2026-10-01")
          @RequestParam
          @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
          LocalDate from,
      @Parameter(description = "Last day of the period, included. ISO 8601", example = "2026-10-31")
          @RequestParam
          @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
          LocalDate to) {
    return IndicatorsResponse.of(consult.execute(CurrentUser.require(), from, to));
  }

  @GetMapping("/export")
  @Operation(
      summary = "Export the usage of the university in a period",
      description =
          """
          US53: the same figures as the consultation, as a CSV file: a header, one line per day \
          and a last line TOTAL for the period. The file is offered as a download.
          """)
  @TenantAndUser
  @ApiResponse(
      responseCode = "200",
      description = "A CSV file with a Content-Disposition",
      content = @Content(mediaType = "text/csv"))
  @ApiResponse(
      responseCode = "400",
      description = "As the consultation",
      content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiError.class)))
  @ApiResponse(
      responseCode = "403",
      description = "The person asking is not an active coordinator of the university",
      content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiError.class)))
  ResponseEntity<byte[]> export(
      @Parameter(description = "First day of the period, included. ISO 8601", example = "2026-10-01")
          @RequestParam
          @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
          LocalDate from,
      @Parameter(description = "Last day of the period, included. ISO 8601", example = "2026-10-31")
          @RequestParam
          @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
          LocalDate to) {
    UsageIndicators indicators = consult.execute(CurrentUser.require(), from, to);
    String filename = "usage-indicators-" + from + "-" + to + ".csv";
    return ResponseEntity.ok()
        .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
        .header(
            HttpHeaders.CONTENT_DISPOSITION,
            ContentDisposition.attachment().filename(filename).build().toString())
        .body(IndicatorsCsv.of(indicators).getBytes(StandardCharsets.UTF_8));
  }
}
