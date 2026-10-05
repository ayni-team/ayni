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
import org.springframework.web.bind.annotation.RestController;
import pe.ayni.recognition.application.ProgressQuery;
import pe.ayni.shared.tenancy.CurrentUser;

/**
 * What a student does about the recognition of the hours they taught.
 *
 * <p>Every endpoint answers for the student making the request, read from {@link CurrentUser}: there
 * is no way to ask about another one.
 */
@RestController
@RequestMapping("/api/v1/recognition")
@Tag(name = "Recognition", description = "Asking the university to recognise the hours taught")
class RecognitionController {

  private final ProgressQuery progress;

  RecognitionController(ProgressQuery progress) {
    this.progress = progress;
  }

  @GetMapping("/progress")
  @Operation(
      summary = "How far the student is from the recognition",
      description =
          """
          US27: the hours the student taught in verified sessions, the hours their university asks \
          for and how many are missing, so they can count how many sessions they still have to \
          teach.

          Only hours earned by teaching count. Credits the university assigned and credits the \
          student bought have no session behind them and are never part of it. The figure is read \
          from the sessions each time, so a session that just completed is already in it.

          When the requirement is met, canRequest is true: that is the moment the student can ask \
          their university for recognition. While the university has no rule, the required and \
          missing hours are absent and canRequest is false.
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
      description = "Student making the request. Read by CurrentUserFilter",
      schema =
          @Schema(type = "string", format = "uuid", example = "11111111-1111-4111-8111-111111111111"))
  @ApiResponse(
      responseCode = "200",
      description = "The progress",
      content = @Content(schema = @Schema(implementation = ProgressResponse.class)))
  @ApiResponse(
      responseCode = "404",
      description = "The person is not a user of this university",
      content = @Content(schema = @Schema(implementation = ApiError.class)))
  ProgressResponse progress() {
    UUID studentId = CurrentUser.require();
    return ProgressResponse.of(progress.of(studentId));
  }
}
