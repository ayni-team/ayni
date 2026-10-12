package pe.ayni.booking.application;

import java.time.LocalDate;
import java.util.UUID;

/** Whether the current tutor is paused today and the end date of that pause, when active. */
public record CurrentAvailabilityPause(
    boolean paused, UUID pauseId, LocalDate startsOn, LocalDate endsOn) {}
