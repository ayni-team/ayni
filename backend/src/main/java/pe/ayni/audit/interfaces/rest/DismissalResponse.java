package pe.ayni.audit.interfaces.rest;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;
import pe.ayni.audit.application.DismissAnomalyUseCase.Dismissal;

/** The decision that closed an alert. */
@Schema(name = "AnomalyDismissal")
public record DismissalResponse(
    @Schema(example = "d0000000-0000-4000-8000-000000000001") UUID id,
    @Schema(example = "DISMISSED") String status,
    @Schema(example = "33333333-3333-4333-8333-333333333333") UUID resolvedBy,
    @Schema(example = "2026-10-13T09:00:00Z") Instant resolvedAt,
    @Schema(example = "They are study partners and the sessions are planned") String reason) {

  static DismissalResponse of(Dismissal dismissal) {
    return new DismissalResponse(
        dismissal.id(),
        dismissal.status(),
        dismissal.resolvedBy(),
        dismissal.resolvedAt(),
        dismissal.reason());
  }
}
