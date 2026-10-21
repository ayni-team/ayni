package pe.ayni.wallet.interfaces.rest;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pe.ayni.wallet.application.VerifyLedgerIntegrity;

/**
 * What a coordinator asks about the integrity of the credit ledger of their university (TS02).
 *
 * <p>Under {@code /api/v1/coordinator}, so access control lets in coordinators only; the use case
 * checks the rest, that the coordinator is active.
 */
@RestController
@RequestMapping("/api/v1/coordinator/ledger")
@Tag(name = "Coordinator ledger", description = "Integrity of the credit ledger of a university")
class CoordinatorLedgerController {

  private final VerifyLedgerIntegrity verify;

  CoordinatorLedgerController(VerifyLedgerIntegrity verify) {
    this.verify = verify;
  }

  @GetMapping("/integrity")
  @Operation(
      summary = "Verify the integrity of the ledger chain (active coordinator only)",
      description =
          """
          Walks the whole chain of the university in order, a page at a time, and checks that \
          every entry matches its own hash and carries the hash of the one before it. When the \
          chain fails, it names the first entry that does not add up and its position. It never \
          returns amounts or people.

          `balanceMismatches` counts the accounts whose entries, added up, differ from the \
          balance the wallet shows. It is zero when every balance can be rebuilt from the history.
          """)
  @ApiResponse(
      responseCode = "200",
      description = "The result of the verification",
      content =
          @Content(
              schema = @Schema(implementation = LedgerIntegrityResponse.class),
              examples = {
                @ExampleObject(
                    name = "An intact chain",
                    value =
                        """
                        {
                          "intact": true,
                          "entriesChecked": 1284,
                          "checkedAt": "2026-10-21T08:00:00Z",
                          "balanceMismatches": 0
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
                          "firstInconsistentPosition": 97,
                          "firstInconsistentSequenceNumber": 98,
                          "balanceMismatches": 1
                        }
                        """)
              }))
  LedgerIntegrityResponse integrity() {
    return LedgerIntegrityResponse.of(verify.asCoordinator());
  }
}
