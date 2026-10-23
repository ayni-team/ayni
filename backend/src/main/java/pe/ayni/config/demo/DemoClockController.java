package pe.ayni.config.demo;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The clock of the demonstration, over HTTP.
 *
 * <p>Only under the {@code demo} profile: under any other the routes do not exist. They are under
 * {@code /api/v1/admin}, so the access filter lets only a platform administrator in.
 */
@RestController
@Profile("demo")
@RequestMapping("/api/v1/admin/demo/clock")
public class DemoClockController {

  private static final String CLOCK_EXAMPLE =
      """
      {"now":"2026-10-23T17:05:00Z","realNow":"2026-10-21T15:05:00Z","offset":"PT50H"}
      """;

  private final DemoClockUseCase demoClock;

  public DemoClockController(DemoClockUseCase demoClock) {
    this.demoClock = demoClock;
  }

  @Operation(
      summary = "Read the demo clock",
      description =
          "The instant every rule of the application reads, the real time and the offset between"
              + " them. Demo profile only.")
  @ApiResponse(
      responseCode = "200",
      description = "The clock",
      content =
          @Content(
              schema = @Schema(implementation = DemoClockView.class),
              examples = @ExampleObject(value = CLOCK_EXAMPLE)))
  @GetMapping
  public DemoClockView read() {
    return demoClock.read();
  }

  @Operation(
      summary = "Move the demo clock by a duration",
      description =
          "Moves the clock forwards, or backwards with a negative duration, from where it is. The"
              + " clock keeps running from there. Demo profile only.")
  @io.swagger.v3.oas.annotations.parameters.RequestBody(
      content =
          @Content(
              schema = @Schema(implementation = AdvanceClockRequest.class),
              examples = {
                @ExampleObject(name = "Two hours ahead", value = "{\"by\": \"PT2H\"}"),
                @ExampleObject(name = "A month ahead", value = "{\"by\": \"P31D\"}"),
                @ExampleObject(name = "One day back", value = "{\"by\": \"-P1D\"}")
              }))
  @ApiResponse(
      responseCode = "200",
      description = "The clock after the move",
      content =
          @Content(
              schema = @Schema(implementation = DemoClockView.class),
              examples = @ExampleObject(value = CLOCK_EXAMPLE)))
  @ApiResponse(
      responseCode = "400",
      description = "by is missing, is not an ISO 8601 duration, or goes too far",
      content = @Content(schema = @Schema(implementation = DemoApiError.class)))
  @PostMapping("/advance")
  public DemoClockView advance(@RequestBody AdvanceClockRequest request) {
    return demoClock.advance(request.duration());
  }

  @Operation(
      summary = "Set the demo clock at an instant",
      description =
          "Takes the clock to that UTC instant; it keeps running from there. Demo profile only.")
  @io.swagger.v3.oas.annotations.parameters.RequestBody(
      content =
          @Content(
              schema = @Schema(implementation = SetClockRequest.class),
              examples =
                  @ExampleObject(
                      name = "Ten minutes before a session at 15:00 in Lima",
                      value = "{\"at\": \"2026-10-22T19:50:00Z\"}")))
  @ApiResponse(
      responseCode = "200",
      description = "The clock at the instant asked for",
      content =
          @Content(
              schema = @Schema(implementation = DemoClockView.class),
              examples = @ExampleObject(value = CLOCK_EXAMPLE)))
  @ApiResponse(
      responseCode = "400",
      description = "at is missing, is not an ISO 8601 instant, or is too far from the real time",
      content = @Content(schema = @Schema(implementation = DemoApiError.class)))
  @PutMapping
  public DemoClockView setTo(@RequestBody SetClockRequest request) {
    return demoClock.setTo(request.instant());
  }

  @Operation(
      summary = "Take the demo clock back to the real time",
      description = "The offset goes back to zero. Demo profile only.")
  @ApiResponse(
      responseCode = "200",
      description = "The clock at the real time",
      content =
          @Content(
              schema = @Schema(implementation = DemoClockView.class),
              examples =
                  @ExampleObject(
                      value =
                          """
                          {"now":"2026-10-21T15:05:00Z","realNow":"2026-10-21T15:05:00Z","offset":"PT0S"}
                          """)))
  @DeleteMapping
  public DemoClockView reset() {
    return demoClock.reset();
  }
}
