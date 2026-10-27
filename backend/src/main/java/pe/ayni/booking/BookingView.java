package pe.ayni.booking;

import java.time.Instant;
import java.util.UUID;

/** A booking as the other modules see it. */
public record BookingView(
    UUID id,
    UUID studentId,
    UUID tutorId,
    UUID catalogItemId,
    Instant startsAt,
    Instant endsAt,
    int hours,
    String needDescription,
    BookingStatus status) {}
