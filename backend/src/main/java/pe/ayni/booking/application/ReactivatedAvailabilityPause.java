package pe.ayni.booking.application;

import pe.ayni.booking.domain.model.AvailabilityPause;

/** An ended pause and the hours restored by applying the weekly rules again. */
public record ReactivatedAvailabilityPause(AvailabilityPause pause, HoursAdjustment hours) {}
