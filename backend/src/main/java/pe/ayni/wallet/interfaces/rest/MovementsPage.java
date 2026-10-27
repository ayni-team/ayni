package pe.ayni.wallet.interfaces.rest;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import pe.ayni.shared.domain.CreditType;
import pe.ayni.wallet.application.MovementView;
import pe.ayni.wallet.domain.model.LedgerDirection;
import pe.ayni.wallet.domain.model.LedgerReason;
import pe.ayni.wallet.domain.model.ReferenceType;

/**
 * A page of the credit history.
 *
 * <p>The page is described with its own fields rather than returned as a Spring {@code Page},
 * whose shape is an implementation detail of the framework and changes between versions. What the
 * API promises should not.
 */
@Schema(name = "MovementsPage", description = "Credit movements, newest first")
public record MovementsPage(
    List<Movement> items,
    @Schema(example = "0") int page,
    @Schema(example = "20") int size,
    @Schema(example = "9") long totalElements,
    @Schema(example = "1") int totalPages) {

  /**
   * One movement.
   *
   * @param creditType the origin of the group the credits moved in or out of
   * @param expiresAt when that group dies, {@code null} when it never does
   * @param referenceId what caused it: the booking, the session, the purchase
   */
  @Schema(name = "Movement")
  public record Movement(
      @Schema(example = "7") long sequenceNumber,
      @Schema(example = "2026-09-18T15:04:21Z") Instant occurredAt,
      @Schema(example = "DEBIT") LedgerDirection direction,
      @Schema(example = "2") int amount,
      @Schema(example = "BOOKING_CHARGE") LedgerReason reason,
      @Schema(nullable = true, example = "SEED") CreditType creditType,
      @Schema(nullable = true, example = "2026-12-15T05:00:00Z") Instant expiresAt,
      @Schema(nullable = true, example = "BOOKING") ReferenceType referenceType,
      @Schema(nullable = true, example = "9f1c2d3e-4b5a-6c7d-8e9f-0a1b2c3d4e5f") UUID referenceId) {}

  static MovementsPage of(Page<MovementView> found) {
    return new MovementsPage(
        found.getContent().stream().map(MovementsPage::describe).toList(),
        found.getNumber(),
        found.getSize(),
        found.getTotalElements(),
        found.getTotalPages());
  }

  private static Movement describe(MovementView view) {
    return new Movement(
        view.sequenceNumber(),
        view.occurredAt(),
        view.direction(),
        view.amount().amount(),
        view.reason(),
        view.creditType(),
        view.expiresAt(),
        view.referenceType(),
        view.referenceId());
  }
}
