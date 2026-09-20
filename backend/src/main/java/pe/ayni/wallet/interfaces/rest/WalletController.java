package pe.ayni.wallet.interfaces.rest;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.time.Instant;
import java.util.UUID;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import pe.ayni.shared.tenancy.CurrentUser;
import pe.ayni.wallet.application.MovementHistoryQuery;
import pe.ayni.wallet.application.WalletBalanceQuery;
import pe.ayni.wallet.domain.model.LedgerReason;

/**
 * What a student can ask about their own credits.
 *
 * <p>The controller does exactly two things: it reads the request and it calls a use case. It never
 * touches an entity or a repository, and it holds no transaction, so the rules stay where they can
 * be tested without HTTP.
 *
 * <p>Both endpoints answer about the student making the request, read from {@link CurrentUser}, and
 * neither takes a student as a parameter. That is deliberate: until sign in exists, an endpoint
 * that could name another student could be pointed at one.
 */
@RestController
@RequestMapping("/api/v1/wallet")
@Validated
@Tag(name = "Wallet", description = "Credits: balance, origin, expiry and history")
class WalletController {

  private final WalletBalanceQuery balance;
  private final MovementHistoryQuery movements;

  WalletController(WalletBalanceQuery balance, MovementHistoryQuery movements) {
    this.balance = balance;
    this.movements = movements;
  }

  @GetMapping
  @Operation(
      summary = "Balance with its origin and expiry dates",
      description =
          """
          What the student can spend right now, split by where the credits came from.

          The balance is not stored: it is derived from the groups of credits that are still \
          alive, so credits that expired have already stopped counting. Earned and purchased \
          credits appear without an expiry date, and earned credits are the only ones that count \
          towards the recognition a university may grant.

          A student with nothing to spend receives a balance of zero and, in `guidance`, how to \
          obtain credits.
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
      description = "The balance, broken down by origin",
      content =
          @Content(
              schema = @Schema(implementation = WalletBalanceResponse.class),
              examples =
                  @ExampleObject(
                      name = "A student with credits of three origins",
                      value =
                          """
                          {
                            "userId": "11111111-1111-4111-8111-111111111111",
                            "available": 12,
                            "earnedTotal": 4,
                            "byType": [
                              {
                                "type": "SEED",
                                "available": 6,
                                "expires": true,
                                "countsTowardsRecognition": false,
                                "groups": [{ "amount": 6, "expiresAt": "2026-12-15T05:00:00Z" }]
                              },
                              {
                                "type": "EARNED",
                                "available": 4,
                                "expires": false,
                                "countsTowardsRecognition": true,
                                "groups": [{ "amount": 4, "expiresAt": null }]
                              },
                              {
                                "type": "PURCHASED",
                                "available": 2,
                                "expires": false,
                                "countsTowardsRecognition": false,
                                "groups": [{ "amount": 2, "expiresAt": null }]
                              }
                            ],
                            "guidance": null
                          }
                          """)))
  WalletBalanceResponse balance() {

    UUID userId = CurrentUser.require();
    return WalletBalanceResponse.of(userId, balance.of(userId), balance.earnedTotal(userId));
  }

  @GetMapping("/movements")
  @Operation(
      summary = "History of credit movements, newest first",
      description =
          """
          Every movement of the student's credits, in the order they happened.

          Nothing is ever removed from this list: credits that expired are no longer available, \
          and both the grant that brought them and the entry that expired them stay visible. A \
          correction is a new entry with reason `ADJUSTMENT`, never a change to an old one.
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
      description = "One page of the history",
      content =
          @Content(
              schema = @Schema(implementation = MovementsPage.class),
              examples =
                  @ExampleObject(
                      name = "An expiry and a refund",
                      value =
                          """
                          {
                            "items": [
                              {
                                "sequenceNumber": 9,
                                "occurredAt": "2026-09-19T05:00:00Z",
                                "direction": "DEBIT",
                                "amount": 3,
                                "reason": "EXPIRY",
                                "creditType": "SEED",
                                "expiresAt": "2026-09-19T05:00:00Z",
                                "referenceType": null,
                                "referenceId": null
                              },
                              {
                                "sequenceNumber": 8,
                                "occurredAt": "2026-09-18T16:20:00Z",
                                "direction": "CREDIT",
                                "amount": 2,
                                "reason": "BOOKING_REFUND",
                                "creditType": "SEED",
                                "expiresAt": "2026-12-15T05:00:00Z",
                                "referenceType": "BOOKING",
                                "referenceId": "9f1c2d3e-4b5a-6c7d-8e9f-0a1b2c3d4e5f"
                              }
                            ],
                            "page": 0,
                            "size": 20,
                            "totalElements": 9,
                            "totalPages": 1
                          }
                          """)))
  MovementsPage movements(
      @Parameter(description = "First moment included, ISO 8601 UTC") @RequestParam(required = false)
          Instant from,
      @Parameter(description = "First moment excluded, ISO 8601 UTC") @RequestParam(required = false)
          Instant to,
      @Parameter(description = "Keep only movements of this kind") @RequestParam(required = false)
          LedgerReason reason,
      @Parameter(description = "Page number, starting at zero") @RequestParam(defaultValue = "0")
          @Min(0)
          int page,
      @Parameter(description = "Movements per page") @RequestParam(defaultValue = "20") @Min(1)
          @Max(100)
          int size) {

    UUID userId = CurrentUser.require();
    return MovementsPage.of(movements.of(userId, from, to, reason, page, size));
  }
}
