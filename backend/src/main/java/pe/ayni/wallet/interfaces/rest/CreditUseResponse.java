package pe.ayni.wallet.interfaces.rest;

import java.time.Instant;
import java.util.UUID;
import io.swagger.v3.oas.annotations.media.Schema;
import pe.ayni.shared.domain.Credits;
import pe.ayni.wallet.application.CreditUseOutcome;
import pe.ayni.wallet.domain.model.CreditUseKind;

@Schema(description = "Credit redemption or donation confirmation and receipt")
public record CreditUseResponse(
    @Schema(example = "db619132-8385-4196-8f1d-24c54b5405c0") UUID id,
    @Schema(example = "4d5c15f0-f7c3-4111-bc5b-e8be420b9e5e") UUID confirmationId,
    @Schema(example = "CAMPUS_BENEFIT_REDEMPTION") CreditUseKind kind,
    @Schema(example = "Library voucher") String benefitName,
    @Schema(example = "2") int credits,
    @Schema(example = "2026-10-06T15:00:00Z") Instant createdAt,
    @Schema(example = "false") boolean confirmationRequired,
    @Schema(nullable = true) String warning,
    @Schema(example = "12") long donationPoolBalance) {

  static CreditUseResponse of(CreditUseOutcome outcome) {
    return new CreditUseResponse(
        outcome.id(),
        outcome.confirmationId(),
        outcome.kind(),
        outcome.benefitName(),
        outcome.credits().amount(),
        outcome.createdAt(),
        outcome.confirmationRequired(),
        outcome.confirmationRequired()
            ? "This operation is final and cannot be reversed. Confirm to continue."
            : null,
        outcome.donationPoolBalance());
  }
}
