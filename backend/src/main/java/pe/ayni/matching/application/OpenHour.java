package pe.ayni.matching.application;

import java.time.Instant;
import java.util.UUID;

/**
 * An hour of a tutor that matching may offer, whichever way it heard of it: announced by {@code
 * HoursGenerated}, or read from {@code BookingApi.openHoursOf}.
 */
public record OpenHour(UUID blockId, Instant startsAt) {}
