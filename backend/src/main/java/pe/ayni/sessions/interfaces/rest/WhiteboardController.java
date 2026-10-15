package pe.ayni.sessions.interfaces.rest;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pe.ayni.sessions.application.WhiteboardEditing;
import pe.ayni.shared.tenancy.CurrentUser;

/**
 * US10: the shared whiteboard of a session, over HTTP. The live channel, where both participants
 * draw at once, is the WebSocket at {@code /api/v1/sessions/{id}/whiteboard/live} (ADR 0009).
 */
@RestController
@RequestMapping("/api/v1/sessions/{id}/whiteboard")
@Tag(name = "Sessions", description = "The live tutoring session and meeting in its room")
class WhiteboardController {

  private final WhiteboardEditing editing;

  WhiteboardController(WhiteboardEditing editing) {
    this.editing = editing;
  }

  @GetMapping
  @Operation(
      summary = "The whiteboard of a session, for its student or its tutor",
      description =
          "Every element drawn and the version they correspond to, in any state of the session: "
              + "while it is in progress, and as it was left once it closed. A session where "
              + "nothing was drawn answers an empty board with hasActivity false. Drawing happens "
              + "on the live channel, a WebSocket at /api/v1/sessions/{id}/whiteboard/live that "
              + "first sends this same board and then every operation, in version order.")
  @ApiResponse(
      responseCode = "200",
      description = "The board",
      content = @Content(schema = @Schema(implementation = WhiteboardResponse.class)))
  @ApiResponse(
      responseCode = "403",
      description = "The reader is neither the student nor the tutor of the session",
      content = @Content(schema = @Schema(implementation = ApiError.class)))
  @ApiResponse(
      responseCode = "404",
      description = "The session does not exist in this university",
      content = @Content(schema = @Schema(implementation = ApiError.class)))
  WhiteboardResponse whiteboard(
      @Parameter(description = "Session identifier") @PathVariable("id") UUID sessionId) {
    return WhiteboardResponse.of(editing.read(sessionId, CurrentUser.require()));
  }

  @PutMapping
  @Operation(
      summary = "Replaces the whole whiteboard of a session in progress",
      description =
          "Sends every element the board will hold together with the version last read. When "
              + "somebody drew since that version the answer is 409 and nothing changes: read it "
              + "again. Both participants connected to the live channel receive the new board.")
  @io.swagger.v3.oas.annotations.parameters.RequestBody(
      content =
          @Content(
              schema = @Schema(implementation = ReplaceWhiteboardRequest.class),
              examples =
                  @ExampleObject(
                      name = "One stroke",
                      value =
                          """
                          {"version": 3,
                           "elements": [{"id": "s-1", "type": "stroke",
                                         "data": {"points": [[10, 10], [40, 25]], "color": "#003A70"}}]}
                          """)))
  @ApiResponse(
      responseCode = "200",
      description = "Replaced; the body is the new board",
      content = @Content(schema = @Schema(implementation = WhiteboardResponse.class)))
  @ApiResponse(
      responseCode = "400",
      description = "An element without id or type, more than 2000 elements or one over 16 KB",
      content = @Content(schema = @Schema(implementation = ApiError.class)))
  @ApiResponse(
      responseCode = "403",
      description = "The caller is neither the student nor the tutor of the session",
      content = @Content(schema = @Schema(implementation = ApiError.class)))
  @ApiResponse(
      responseCode = "404",
      description = "The session does not exist in this university",
      content = @Content(schema = @Schema(implementation = ApiError.class)))
  @ApiResponse(
      responseCode = "409",
      description = "The session is not in progress, or the board changed since that version",
      content = @Content(schema = @Schema(implementation = ApiError.class)))
  WhiteboardResponse replace(
      @Parameter(description = "Session identifier") @PathVariable("id") UUID sessionId,
      @Valid @RequestBody ReplaceWhiteboardRequest request) {
    return WhiteboardResponse.of(
        editing.replace(sessionId, CurrentUser.require(), request.elements(), request.version()));
  }
}
