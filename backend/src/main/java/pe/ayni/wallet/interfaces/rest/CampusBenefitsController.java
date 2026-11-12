package pe.ayni.wallet.interfaces.rest;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pe.ayni.shared.domain.Credits;
import pe.ayni.shared.tenancy.CurrentUser;
import pe.ayni.wallet.application.CampusBenefitCatalog;
import pe.ayni.wallet.application.CreditUseService;

@RestController
@RequestMapping("/api/v1/wallet")
@Validated
@Tag(name = "Wallet redemptions", description = "Campus benefit redemption and student donations")
class CampusBenefitsController {

  private final CampusBenefitCatalog benefits;
  private final CreditUseService creditUses;

  CampusBenefitsController(CampusBenefitCatalog benefits, CreditUseService creditUses) {
    this.benefits = benefits;
    this.creditUses = creditUses;
  }

  @GetMapping("/benefits")
  @Operation(summary = "List active campus benefits available for earned-credit redemption")
  List<CampusBenefitResponse> benefits() {
    return benefits.available().stream().map(CampusBenefitResponse::of).toList();
  }

  @PostMapping("/benefits/{benefitId}/redemptions")
  @Operation(
      summary = "Redeem earned credits for a campus benefit",
      description =
          "Send confirmed=false to receive the irreversible-operation warning without spending "
              + "credits. Submit its confirmationId with the same Idempotency-Key and confirmed=true.")
  @ApiResponse(
      responseCode = "200",
      description = "The warning to confirm, when confirmed is false, or the receipt",
      content = @Content(schema = @Schema(implementation = CreditUseResponse.class)))
  @ApiResponse(
      responseCode = "409",
      description =
          "Refused by the state of the wallet or of the confirmation. The code says which:"
              + " INSUFFICIENT_EARNED_CREDITS with missingCredits, CONFIRMATION_REQUIRED,"
              + " CONFIRMATION_INVALID, CONFIRMATION_EXPIRED, CONFIRMATION_MISMATCH,"
              + " CONFIRMATION_USED or IDEMPOTENCY_CONFLICT",
      content =
          @Content(
              mediaType = "application/json",
              schema = @Schema(implementation = ApiError.class),
              examples = {
                @ExampleObject(
                    name = "Not enough earned credits",
                    value =
                        """
                        {
                          "timestamp": "2026-11-10T15:00:00Z",
                          "status": 409,
                          "error": "Conflict",
                          "message": "Insufficient earned credits, missing 2",
                          "path": "/api/v1/wallet/benefits/9f1c2d3e-4b5a-6c7d-8e9f-0a1b2c3d4e5f/redemptions",
                          "code": "INSUFFICIENT_EARNED_CREDITS",
                          "missingCredits": 2
                        }
                        """),
                @ExampleObject(
                    name = "The warning expired",
                    value =
                        """
                        {
                          "timestamp": "2026-11-10T15:11:00Z",
                          "status": 409,
                          "error": "Conflict",
                          "message": "The confirmation has expired; request a new one",
                          "path": "/api/v1/wallet/benefits/9f1c2d3e-4b5a-6c7d-8e9f-0a1b2c3d4e5f/redemptions",
                          "code": "CONFIRMATION_EXPIRED"
                        }
                        """)
              }))
  CreditUseResponse redeemBenefit(
      @PathVariable UUID benefitId,
      @Valid @RequestBody CreditUseRequest request,
      @RequestHeader("Idempotency-Key") @Size(min = 1, max = 64) String idempotencyKey) {
    return CreditUseResponse.of(
        creditUses.redeemBenefit(
            CurrentUser.require(),
            benefitId,
            idempotencyKey,
            request.confirmationId(),
            request.confirmed()));
  }

  @PostMapping("/donations")
  @Operation(
      summary = "Donate earned credits to the incoming-student pool",
      description =
          "Send confirmed=false to receive the irreversible-operation warning without spending "
              + "credits. Submit its confirmationId with the same Idempotency-Key and confirmed=true.")
  @ApiResponse(
      responseCode = "200",
      description = "The warning to confirm, when confirmed is false, or the receipt",
      content = @Content(schema = @Schema(implementation = CreditUseResponse.class)))
  @ApiResponse(
      responseCode = "409",
      description =
          "Refused by the state of the wallet or of the confirmation. The code says which:"
              + " INSUFFICIENT_EARNED_CREDITS with missingCredits, CONFIRMATION_REQUIRED,"
              + " CONFIRMATION_INVALID, CONFIRMATION_EXPIRED, CONFIRMATION_MISMATCH,"
              + " CONFIRMATION_USED or IDEMPOTENCY_CONFLICT",
      content =
          @Content(
              mediaType = "application/json",
              schema = @Schema(implementation = ApiError.class),
              examples = {
                @ExampleObject(
                    name = "Not enough earned credits",
                    value =
                        """
                        {
                          "timestamp": "2026-11-10T15:00:00Z",
                          "status": 409,
                          "error": "Conflict",
                          "message": "Insufficient earned credits, missing 2",
                          "path": "/api/v1/wallet/donations",
                          "code": "INSUFFICIENT_EARNED_CREDITS",
                          "missingCredits": 2
                        }
                        """),
                @ExampleObject(
                    name = "The warning expired",
                    value =
                        """
                        {
                          "timestamp": "2026-11-10T15:11:00Z",
                          "status": 409,
                          "error": "Conflict",
                          "message": "The confirmation has expired; request a new one",
                          "path": "/api/v1/wallet/donations",
                          "code": "CONFIRMATION_EXPIRED"
                        }
                        """)
              }))
  CreditUseResponse donate(
      @Valid @RequestBody DonationRequest request,
      @RequestHeader("Idempotency-Key") @Size(min = 1, max = 64) String idempotencyKey) {
    return CreditUseResponse.of(
        creditUses.donate(
            CurrentUser.require(),
            Credits.of(request.credits()),
            idempotencyKey,
            request.confirmationId(),
            request.confirmed()));
  }

  @GetMapping("/donation-pool")
  @Operation(summary = "Read the incoming-student donation pool balance")
  DonationPoolResponse donationPool() {
    return DonationPoolResponse.of(creditUses.donationPool());
  }

  @GetMapping("/donations")
  @Operation(summary = "List the current student's donations to the incoming-student pool")
  List<CreditUseResponse> donations() {
    return creditUses.myDonations(CurrentUser.require()).stream()
        .map(CreditUseResponse::of)
        .toList();
  }
}
