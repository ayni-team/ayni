package pe.ayni.wallet.interfaces.rest;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;

@Schema(description = "Donation amount and the student's explicit confirmation")
public record DonationRequest(
    @Schema(example = "2") @Min(1) int credits,
    @Schema(example = "true") Boolean confirmed,
    @Schema(example = "4d5c15f0-f7c3-4111-bc5b-e8be420b9e5e") UUID confirmationId) {

  @NotNull
  public Boolean confirmed() {
    return confirmed;
  }
}
