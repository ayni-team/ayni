package pe.ayni.sessions.interfaces.rest;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import pe.ayni.sessions.application.PracticeRooms;
import pe.ayni.shared.tenancy.CurrentUser;

/**
 * Opens practice rooms: a tool of the team to try the screen of a session, with its video and its
 * whiteboard, between two accounts and without leaving any record ({@link PracticeRooms}).
 */
@RestController
@RequestMapping("/api/v1/practice-rooms")
@Tag(name = "Sessions", description = "The live tutoring session and meeting in its room")
class PracticeRoomsController {

  private final PracticeRooms practice;

  PracticeRoomsController(PracticeRooms practice) {
    this.practice = practice;
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  @Operation(
      operationId = "openPracticeRoom",
      summary = "Open a practice room",
      description =
          "A tool of the team, not a feature of the product: opens a room two accounts of the "
              + "same university use through the screen of a session, at any time, to try its "
              + "video call and its whiteboard. It lives only in the memory of the backend: no "
              + "booking, no session, no row of any table, no credit, no event and no email. The "
              + "endpoints of a session answer for its id as they do for a session; the first "
              + "person to join is the tutor, the second the student, and anybody else is refused "
              + "with 403. Any six digit code confirms presence. When both end it, it answers "
              + "COMPLETED, closes the whiteboard and is forgotten; otherwise it is forgotten four "
              + "hours after it was opened, or when the backend restarts. Only while "
              + "ayni.sessions.practice-rooms.enabled is true (AYNI_PRACTICE_ROOMS, on under the "
              + "demo profile); off, this answers 404. No request body.")
  @ApiResponse(
      responseCode = "201",
      description = "The room, empty",
      content =
          @Content(
              schema = @Schema(implementation = PracticeRoomResponse.class),
              examples =
                  @ExampleObject(
                      value =
                          """
                          {"sessionId":"9d4c1f0e-2b3a-4c5d-8e6f-7a8b9c0d1e2f",
                           "path":"/sesion/9d4c1f0e-2b3a-4c5d-8e6f-7a8b9c0d1e2f"}
                          """)))
  @ApiResponse(
      responseCode = "404",
      description = "Practice rooms are not enabled in this backend",
      content = @Content(schema = @Schema(implementation = ApiError.class)))
  @ApiResponse(
      responseCode = "409",
      description =
          "As many practice rooms are open as the backend keeps at once "
              + "(ayni.sessions.practice-rooms.max-open, 20): end one or wait until one expires",
      content = @Content(schema = @Schema(implementation = ApiError.class)))
  PracticeRoomResponse open() {
    CurrentUser.require();
    UUID id = practice.open();
    return new PracticeRoomResponse(id, "/sesion/" + id);
  }
}
