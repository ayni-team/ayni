package pe.ayni.sessions.interfaces.rest;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pe.ayni.sessions.application.ReviewSessionQuery;
import pe.ayni.shared.tenancy.CurrentUser;

/** US12: reviewing a session of one's history. */
@RestController
@RequestMapping("/api/v1/sessions/{id}/review")
@Tag(name = "Sessions", description = "The live tutoring session and meeting in its room")
class SessionReviewController {

  private final ReviewSessionQuery review;

  SessionReviewController(ReviewSessionQuery review) {
    this.review = review;
  }

  @GetMapping
  @Operation(
      summary = "Reviews a session of the reader's history",
      description =
          "US12: the whiteboard as it was left, the support material prepared from the need, the "
              + "need itself and how long the session really lasted, from both participants being "
              + "in to its close. A session that did not take place answers its state and a note "
              + "of what happened, cancelled or abandoned and who did not come, and no board, "
              + "instead of an empty workspace. Only the student and the tutor of the session.")
  @ApiResponse(
      responseCode = "200",
      description = "The session as it was left",
      content = @Content(schema = @Schema(implementation = SessionReviewResponse.class)))
  @ApiResponse(
      responseCode = "403",
      description = "The reader is neither the student nor the tutor of the session",
      content = @Content(schema = @Schema(implementation = ApiError.class)))
  @ApiResponse(
      responseCode = "404",
      description = "The session does not exist in this university",
      content = @Content(schema = @Schema(implementation = ApiError.class)))
  SessionReviewResponse review(
      @Parameter(description = "Session identifier") @PathVariable("id") UUID sessionId) {
    return SessionReviewResponse.of(review.execute(sessionId, CurrentUser.require()));
  }
}
