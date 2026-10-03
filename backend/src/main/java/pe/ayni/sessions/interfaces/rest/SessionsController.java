package pe.ayni.sessions.interfaces.rest;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
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
import org.springframework.web.bind.annotation.RestController;
import pe.ayni.sessions.application.ConfirmPresenceUseCase;
import pe.ayni.sessions.application.EndSessionUseCase;
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
  private final ConfirmPresenceUseCase confirmPresence;
  private final EndSessionUseCase endSession;

  SessionsController(
      SessionDetailsQuery sessionDetails,
      JoinSessionUseCase joinSession,
      ConfirmPresenceUseCase confirmPresence,
      EndSessionUseCase endSession) {
    this.sessionDetails = sessionDetails;
    this.joinSession = joinSession;
    this.confirmPresence = confirmPresence;
    this.endSession = endSession;
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
              + "start until the scheduled end, and answers with the room name and both arrival "
              + "times. The session starts and SessionStarted is published once both participants "
              + "check in by the ten-minute deadline. Rejoining after a dropped connection changes "
              + "nothing.")
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

  @PostMapping("/{id}/presence")
  @Operation(
      summary = "Confirm presence with the emailed code",
      description =
          "Five minutes after the scheduled start each participant receives a six digit code by "
              + "email. Typing it in here, while the session is in progress and within fifteen "
              + "minutes of receiving it, confirms their presence. Only a participant who joined "
              + "the session can confirm. A wrong code spends one of five attempts; after the "
              + "fifth the code is useless. Typing a confirmed code again changes nothing.")
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
      description = "Presence confirmed",
      content = @Content(schema = @Schema(implementation = PresenceResponse.class)))
  @ApiResponse(
      responseCode = "400",
      description = "The code is not six digits, a header is missing or the id is not a UUID",
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
      description =
          "Presence cannot be confirmed now: the code was not sent yet, it expired, its attempts "
              + "are spent, the participant never joined, or the session is not in progress",
      content =
          @Content(
              schema = @Schema(implementation = ApiError.class),
              examples =
                  @ExampleObject(
                      name = "Expired",
                      value =
                          """
                          {"timestamp":"2026-09-30T20:25:00Z","status":409,"error":"Conflict",
                           "message":"This presence code expired at 2026-09-30T20:20:00Z",
                           "path":"/api/v1/sessions/5b1f0c2a-4d6e-4c7d-8e9f-0a1b2c3d4e5f/presence"}
                          """)))
  @ApiResponse(
      responseCode = "422",
      description = "Not the code that was sent. The attempt counts, and the message says how many "
          + "are left",
      content =
          @Content(
              schema = @Schema(implementation = ApiError.class),
              examples =
                  @ExampleObject(
                      name = "Wrong code",
                      value =
                          """
                          {"timestamp":"2026-09-30T20:06:00Z","status":422,
                           "error":"Unprocessable Entity",
                           "message":"That is not the code you were sent. Attempts left: 4",
                           "path":"/api/v1/sessions/5b1f0c2a-4d6e-4c7d-8e9f-0a1b2c3d4e5f/presence"}
                          """)))
  PresenceResponse confirmPresence(
      @Parameter(description = "Session identifier",
              example = "5b1f0c2a-4d6e-4c7d-8e9f-0a1b2c3d4e5f")
          @PathVariable("id")
          UUID sessionId,
      @Valid @RequestBody ConfirmPresenceRequest request) {
    return PresenceResponse.of(
        confirmPresence.execute(sessionId, CurrentUser.require(), request.code()));
  }

  @PostMapping("/{id}/end")
  @Operation(
      summary = "Confirm the session is over",
      description =
          "Records that this participant confirms the end. When both have, the session closes: "
              + "COMPLETED if both confirmed their presence with the emailed code, and the tutor "
              + "is credited the booked hours as earned credits; UNVERIFIED otherwise, and the "
              + "student is refunded. If only one confirms, the session closes on its own fifteen "
              + "minutes after the booked hour, the same way. Only a participant who joined can "
              + "confirm. Confirming again changes nothing.")
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
      description = "The end is confirmed; the status says whether the session closed",
      content = @Content(schema = @Schema(implementation = EndedSessionResponse.class)))
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
      description = "The session is not in progress, or the person never joined it",
      content =
          @Content(
              schema = @Schema(implementation = ApiError.class),
              examples =
                  @ExampleObject(
                      name = "Already closed",
                      value =
                          """
                          {"timestamp":"2026-09-30T21:20:00Z","status":409,"error":"Conflict",
                           "message":"This session is completed: only a session in progress can be ended",
                           "path":"/api/v1/sessions/5b1f0c2a-4d6e-4c7d-8e9f-0a1b2c3d4e5f/end"}
                          """)))
  EndedSessionResponse end(
      @Parameter(description = "Session identifier",
              example = "5b1f0c2a-4d6e-4c7d-8e9f-0a1b2c3d4e5f")
          @PathVariable("id")
          UUID sessionId) {
    return EndedSessionResponse.of(endSession.execute(sessionId, CurrentUser.require()));
  }
}
