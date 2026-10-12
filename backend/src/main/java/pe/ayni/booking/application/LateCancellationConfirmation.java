package pe.ayni.booking.application;

import java.time.Instant;
import java.util.UUID;

/**
 * What a caller is warned about before a late cancellation is carried out.
 *
 * @param refundWillBeIssued whether the student gets their credits back: always when the tutor
 *     cancels, never when the student cancels this late
 * @param incidentWillBeRecorded whether the cancellation is kept against the tutor's record
 */
public record LateCancellationConfirmation(
    UUID bookingId, Instant startsAt, boolean refundWillBeIssued, boolean incidentWillBeRecorded) {}
