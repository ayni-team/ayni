package pe.ayni.sessions.interfaces.rest;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pe.ayni.sessions.application.JoinSessionUseCase;
import pe.ayni.sessions.application.SessionDetailsQuery;
import pe.ayni.shared.tenancy.CurrentUser;

/**
 * The live session, for its two participants.
 *
 * <p>The controller only translates HTTP into use cases. The reader is always {@link CurrentUser}:
 * whether they belong to the session is decided by the session, and anybody else is refused, link or
 * no link.
 */
@RestController
@RequestMapping("/api/v1/sessions")
@Tag(name = "Sessions", description = "The live tutoring session and meeting in its room")
class SessionsController {

  private final SessionDetailsQuery sessionDetails;
  private final JoinSessionUseCase joinSession;

  SessionsController(SessionDetailsQuery sessionDetails, JoinSessionUseCase joinSession) {
    this.sessionDetails = sessionDetails;
    this.joinSession = joinSession;
  }

  @GetMapping("/{id}")
  @Operation(
      summary = "A session, for its student or its tutor",
      description =
          "The session of a booking with its schedule, its state, when the room opens and what "
              + "the student needs help with, so the tutor arrives prepared. Only the two "
              + "participants can read it. The room name is not included: it is handed over by "
              + "joining.")
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
      description = "The student or the tutor of the session. Read by CurrentUserFilter",
      schema = @Schema(type = "string", format = "uuid",
          example = "22222222-2222-4222-8222-222222222222"))
  @ApiResponse(
      responseCode = "200",
      description = "The session",
      content = @Content(schema = @Schema(implementation = SessionResponse.class)))
  @ApiResponse(
      responseCode = "400",
      description = "A header is missing or the id is not a UUID",
      content = @Content(schema = @Schema(implementation = ApiError.class)))
  @ApiResponse(
      responseCode = "403",
      description = "The reader is neither the student nor the tutor of the session",
      content = @Content(schema = @Schema(implementation = ApiError.class)))
  @ApiResponse(
      responseCode = "404",
      description = "The session does not exist in this university",
      content = @Content(schema = @Schema(implementation = ApiError.class)))
  SessionResponse session(
      @Parameter(description = "Session identifier",
              example = "5b1f0c2a-4d6e-4c7d-8e9f-0a1b2c3d4e5f")
          @PathVariable("id")
          UUID sessionId) {
    return SessionResponse.of(sessionDetails.execute(sessionId, CurrentUser.require()));
  }

  @PostMapping("/{id}/join")
  @Operation(
      summary = "Join the session's room",
      description =
          "Lets the student or the tutor into the video call, from fifteen minutes before the "
              + "start until the scheduled end, and answers with the room name. The first "
              + "participant to join starts the session and SessionStarted is published. Joining "
              + "again, after a dropped connection, changes nothing.")
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
      description = "The student or the tutor of the session. Read by CurrentUserFilter",
      schema = @Schema(type = "string", format = "uuid",
          example = "11111111-1111-4111-8111-111111111111"))
  @ApiResponse(
      responseCode = "200",
      description = "In the room",
      content = @Content(schema = @Schema(implementation = JoinedSessionResponse.class)))
  @ApiResponse(
      responseCode = "400",
      description = "A header is missing or the id is not a UUID",
      content = @Content(schema = @Schema(implementation = ApiError.class)))
  @ApiResponse(
      responseCode = "403",
      description = "The person is neither the student nor the tutor of the session",
      content = @Content(schema = @Schema(implementation = ApiError.class)))
  @ApiResponse(
      responseCode = "404",
      description = "The session does not exist in this university",
      content = @Content(schema = @Schema(implementation = ApiError.class)))
  @ApiResponse(
      responseCode = "409",
      description = "The room is not open: too early, already over, or the session will not happen",
      content =
          @Content(
              schema = @Schema(implementation = ApiError.class),
              examples =
                  @ExampleObject(
                      name = "Too early",
                      value =
                          """
                          {"timestamp":"2026-09-30T19:30:00Z","status":409,"error":"Conflict",
                           "message":"The room opens fifteen minutes before the session starts, at 2026-09-30T19:45:00Z",
                           "path":"/api/v1/sessions/5b1f0c2a-4d6e-4c7d-8e9f-0a1b2c3d4e5f/join"}
                          """)))
  JoinedSessionResponse join(
      @Parameter(description = "Session identifier",
              example = "5b1f0c2a-4d6e-4c7d-8e9f-0a1b2c3d4e5f")
          @PathVariable("id")
          UUID sessionId) {
    return JoinedSessionResponse.of(joinSession.execute(sessionId, CurrentUser.require()));
  }
}
