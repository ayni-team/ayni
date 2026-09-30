package pe.ayni.booking.interfaces.rest;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;
import pe.ayni.booking.application.LateCancellationConfirmation;

/** Warning returned before a late cancellation is carried out. */
@Schema(name = "LateCancellationConfirmation")
record LateCancellationResponse(
    @Schema(description = "Booking that would be cancelled.",
        example = "d1f0c2a4-5b6e-4c7d-8e9f-0a1b2c3d4e5f")
    UUID bookingId,
    @Schema(description = "Scheduled start of the tutoring session in UTC.",
        example = "2026-09-29T23:00:00Z")
    Instant startsAt,
    @Schema(description = "Whether the cancellation is inside the twelve-hour late window.",
        example = "true")
    boolean late,
    @Schema(description = "Late cancellations under this policy do not receive a credit refund.",
        example = "false")
    boolean refundWillBeIssued,
    @Schema(description = "Whether the caller must explicitly confirm before cancellation.",
        example = "true")
    boolean confirmationRequired,
    @Schema(
        description = "Repeat the DELETE request with this confirmation query parameter.",
        example = "DELETE /api/v1/bookings/d1f0c2a4-5b6e-4c7d-8e9f-0a1b2c3d4e5f?confirmLate=true")
    String confirmationRequest) {

  static LateCancellationResponse of(LateCancellationConfirmation confirmation) {
    return new LateCancellationResponse(
        confirmation.bookingId(),
        confirmation.startsAt(),
        true,
        confirmation.refundWillBeIssued(),
        true,
        "DELETE /api/v1/bookings/" + confirmation.bookingId() + "?confirmLate=true");
  }
}
