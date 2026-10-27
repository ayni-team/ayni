package pe.ayni.booking.application;

import pe.ayni.booking.domain.model.AvailabilityException;

/** A date exception that was just saved, and what it did to the hours of that date. */
public record DeclaredException(AvailabilityException exception, HoursAdjustment hours) {}
