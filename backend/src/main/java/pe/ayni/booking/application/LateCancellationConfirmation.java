package pe.ayni.booking.application;

import java.time.Instant;
import java.util.UUID;

/** Information needed to ask the caller to confirm a late cancellation. */
public record LateCancellationConfirmation(
    UUID bookingId, Instant startsAt, boolean refundWillBeIssued) {}
