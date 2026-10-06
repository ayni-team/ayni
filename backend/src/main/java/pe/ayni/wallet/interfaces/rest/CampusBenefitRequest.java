package pe.ayni.wallet.interfaces.rest;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "University campus benefit")
public record CampusBenefitRequest(
    @Schema(example = "Library voucher") @NotBlank @Size(max = 120) String name,
    @Schema(example = "One hour in a study room") @NotBlank @Size(max = 500) String description,
    @Schema(example = "2") @Min(1) int creditsCost,
    @Schema(example = "true") boolean active) {}
