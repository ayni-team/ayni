package pe.ayni.audit.interfaces.rest;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.Instant;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import pe.ayni.audit.application.ConsultActivityLogUseCase;
import pe.ayni.audit.application.ConsultActivityLogUseCase.Filter;
import pe.ayni.shared.tenancy.CurrentUser;

/** What a coordinator reads of the activity log of their university. */
@RestController
@RequestMapping("/api/v1/coordinator/audit-log")
@Tag(name = "Audit log", description = "What happened with credits, bookings, sessions and recognition")
class AuditLogController {

  private final ConsultActivityLogUseCase consult;

  AuditLogController(ConsultActivityLogUseCase consult) {
    this.consult = consult;
  }

  @GetMapping
  @Operation(
      summary = "The activity log of the university",
      description =
          """
          US48: what happened with credits, bookings, sessions, skills and recognition, the most \
          recent first: when, who did it and in what role, which action and on which resource. \
          Filter by person, by resource, by period or by any mix of them.

          Only an active coordinator of the university may read it. Anybody else is refused and \
          the attempt is written down in the log. Entries are only added: what is read here cannot \
          have been changed.
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
      description = "A page of entries",
      content = @Content(schema = @Schema(implementation = AuditLogResponse.class)))
  @ApiResponse(
      responseCode = "400",
      description =
          "A header is missing, an identifier is not a UUID, a date is not ISO 8601 or the period"
              + " ends before it starts",
      content = @Content(schema = @Schema(implementation = ApiError.class)))
  @ApiResponse(
      responseCode = "403",
      description =
          "The person asking is not an active coordinator of the university. The attempt is"
              + " recorded",
      content = @Content(schema = @Schema(implementation = ApiError.class)))
  AuditLogResponse log(
      @Parameter(description = "Only what this person did") @RequestParam(required = false)
          UUID actorId,
      @Parameter(description = "Only entries about this kind of resource, such as BOOKING")
          @RequestParam(required = false)
          String resourceType,
      @Parameter(description = "Only entries about this resource") @RequestParam(required = false)
          UUID resourceId,
      @Parameter(description = "From this moment on, inclusive. ISO 8601 UTC")
          @RequestParam(required = false)
          @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
          Instant from,
      @Parameter(description = "Until this moment, exclusive. ISO 8601 UTC")
          @RequestParam(required = false)
          @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
          Instant to,
      @Parameter(description = "Page, from zero") @RequestParam(defaultValue = "0") int page,
      @Parameter(description = "Entries per page, at most 100") @RequestParam(defaultValue = "20")
          int size) {
    UUID coordinatorId = CurrentUser.require();
    return AuditLogResponse.of(
        consult.execute(coordinatorId, new Filter(actorId, resourceType, resourceId, from, to), page, size));
  }
}
