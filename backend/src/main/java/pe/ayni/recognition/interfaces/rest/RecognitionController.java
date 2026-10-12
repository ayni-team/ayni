package pe.ayni.recognition.interfaces.rest;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import pe.ayni.recognition.application.MyRequestsQuery;
import pe.ayni.recognition.application.ProgressQuery;
import pe.ayni.recognition.application.SubmitRecognitionRequestUseCase;
import pe.ayni.recognition.application.RequestFile;
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

  private final MyRequestsQuery myRequests;
  private final ProgressQuery progress;
  private final SubmitRecognitionRequestUseCase submitRequest;

  RecognitionController(
      MyRequestsQuery myRequests,
      ProgressQuery progress,
      SubmitRecognitionRequestUseCase submitRequest) {
    this.myRequests = myRequests;
    this.progress = progress;
    this.submitRequest = submitRequest;
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
          from the sessions each time, so a session that just completed is already in it. Sessions \
          a request already used are not counted again.

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

  @PostMapping("/requests")
  @ResponseStatus(HttpStatus.CREATED)
  @Operation(
      summary = "Asks the university to recognise the hours taught",
      description =
          """
          US28: a student who reached the hours their university asks for submits a request. It is \
          registered with the total of hours, the sessions that back it and the ratings the tutor \
          received, copied now so that the coordinator reads what was presented.

          The sessions are the oldest ones no request has used, until the hours asked for are \
          reached; the student keeps the rest for the next request. A session backs one request and \
          never a second one. The credits are not consumed: they stay available to book sessions.

          With fewer hours than the university asks for, nothing is registered and the answer says \
          how many are missing (409, missingHours).
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
      responseCode = "201",
      description = "The request was registered",
      content = @Content(schema = @Schema(implementation = RequestResponse.class)))
  @ApiResponse(
      responseCode = "404",
      description = "The person is not a user of this university",
      content = @Content(schema = @Schema(implementation = ApiError.class)))
  @ApiResponse(
      responseCode = "409",
      description =
          "The student has not taught the hours asked for (and the answer says how many are missing),"
              + " or the university has not opened recognition",
      content = @Content(schema = @Schema(implementation = InsufficientHoursError.class)))
  RequestResponse submit() {
    UUID studentId = CurrentUser.require();
    RequestFile submitted = submitRequest.execute(studentId);
    return RequestResponse.of(submitted.request(), submitted.sessions());
  }

  @GetMapping("/requests/mine")
  @Operation(
      summary = "The student's own recognition requests",
      description =
          """
          US28: the requests the student submitted, the latest first, each with its state and, once \
          the university decided, the decision and its reason.

          The figures and the sessions are the ones the request was submitted with. Only the \
          student's own requests are returned.
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
      description = "The requests, possibly none",
      content = @Content(array = @ArraySchema(schema = @Schema(implementation = RequestResponse.class))))
  @ApiResponse(
      responseCode = "404",
      description = "The person is not a user of this university",
      content = @Content(schema = @Schema(implementation = ApiError.class)))
  List<RequestResponse> mine() {
    UUID studentId = CurrentUser.require();
    return myRequests.of(studentId).stream()
        .map(file -> RequestResponse.of(file.request(), file.sessions()))
        .toList();
  }
}
