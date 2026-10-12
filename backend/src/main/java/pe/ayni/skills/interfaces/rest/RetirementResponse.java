package pe.ayni.skills.interfaces.rest;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;
import pe.ayni.skills.application.RetireCatalogItemUseCase.Retirement;

/** What was retired, and how many tutors lost their offer of it. */
@Schema(name = "CatalogRetirement", description = "The outcome of retiring a catalogue item")
public record RetirementResponse(
    @Schema(example = "b0000000-0000-4000-8000-000000000101") UUID catalogItemId,
    @Schema(example = "RETIRED") String status,
    @Schema(example = "12") long tutorsAffected,
    @Schema(example = "2026-10-05T09:00:00Z") Instant retiredAt) {

  static RetirementResponse of(Retirement retirement) {
    return new RetirementResponse(
        retirement.item().getId(),
        retirement.item().getStatus().name(),
        retirement.tutorsAffected(),
        retirement.retiredAt());
  }
}
