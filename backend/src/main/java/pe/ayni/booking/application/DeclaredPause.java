package pe.ayni.booking.application;

import pe.ayni.booking.domain.model.AvailabilityPause;

/** A pause that was just saved, and what it did to the hours that already existed. */
public record DeclaredPause(AvailabilityPause pause, HoursAdjustment hours) {}
