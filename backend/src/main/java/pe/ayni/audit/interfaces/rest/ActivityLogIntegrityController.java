package pe.ayni.audit.interfaces.rest;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pe.ayni.audit.application.VerifyActivityLogIntegrityUseCase;
import pe.ayni.shared.tenancy.CurrentUser;

/**
 * What a coordinator asks about the integrity of the activity log of their university (US48).
 *
 * <p>Under {@code /api/v1/coordinator}, so access control lets in coordinators only; the use case
 * checks the rest, that the coordinator is active, and writes a refusal down.
 */
@RestController
@RequestMapping("/api/v1/coordinator/audit-log/integrity")
@Tag(name = "Audit log", description = "What happened with credits, bookings, sessions and recognition")
class ActivityLogIntegrityController {

  private final VerifyActivityLogIntegrityUseCase verify;

  ActivityLogIntegrityController(VerifyActivityLogIntegrityUseCase verify) {
    this.verify = verify;
  }

  @GetMapping
  @Operation(
      summary = "Verify the chain of the activity log (active coordinator only)",
      description =
          """
          US48: walks the chain of the log of the university in the order it was written, a page \
          at a time, and checks that every entry matches its own hash and carries the hash of the \
          one before it. When it does not hold, it names the first entry that does not fit the \
          sequence: one that was changed, the one after an entry that was removed, or one slipped \
          in behind the application.

          The entries written before the log was chained carry no hash. They are not counted and \
          never reported as altered.

          Only an active coordinator of the university may ask. Anybody else is refused and the \
          attempt is written down in the log, as for the log itself.
          """)
  @ApiResponse(
      responseCode = "200",
      description = "The result of the verification",
      content =
          @Content(
              schema = @Schema(implementation = ActivityLogIntegrityResponse.class),
              examples = {
                @ExampleObject(
                    name = "An intact chain",
                    value =
                        """
                        {
                          "intact": true,
                          "entriesChecked": 1284,
                          "checkedAt": "2026-10-21T08:00:00Z"
                        }
                        """),
                @ExampleObject(
                    name = "A chain broken at its 97th entry",
                    value =
                        """
                        {
                          "intact": false,
                          "entriesChecked": 97,
                          "checkedAt": "2026-10-21T08:00:00Z",
                          "firstInconsistentEntryId": "6b1f3c0e-7a52-4c1d-9a55-0f8d6a2b9c11",
                          "firstInconsistentOccurredAt": "2026-10-14T16:20:00Z",
                          "firstInconsistentSequenceNumber": 97
                        }
                        """)
              }))
  @ApiResponse(
      responseCode = "403",
      description =
          "The person asking is not an active coordinator of the university. The attempt is"
              + " recorded",
      content = @Content(schema = @Schema(implementation = ApiError.class)))
  ActivityLogIntegrityResponse integrity() {
    return ActivityLogIntegrityResponse.of(verify.execute(CurrentUser.require()));
  }
}
