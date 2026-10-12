package pe.ayni.wallet.interfaces.rest;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
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

  @PostMapping("/benefits")
  @Operation(summary = "Create a campus benefit (university coordinator only)")
  CampusBenefitResponse createBenefit(@Valid @RequestBody CampusBenefitRequest request) {
    return CampusBenefitResponse.of(
        benefits.create(
            request.name(), request.description(), request.creditsCost(), request.active()));
  }

  @PutMapping("/benefits/{benefitId}")
  @Operation(summary = "Update or deactivate a campus benefit (university coordinator only)")
  CampusBenefitResponse updateBenefit(
      @PathVariable UUID benefitId, @Valid @RequestBody CampusBenefitRequest request) {
    return CampusBenefitResponse.of(
        benefits.update(
            benefitId,
            request.name(),
            request.description(),
            request.creditsCost(),
            request.active()));
  }

  @PostMapping("/benefits/{benefitId}/redemptions")
  @Operation(
      summary = "Redeem earned credits for a campus benefit",
      description =
          "Send confirmed=false to receive the irreversible-operation warning without spending "
              + "credits. Submit its confirmationId with the same Idempotency-Key and confirmed=true.")
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
