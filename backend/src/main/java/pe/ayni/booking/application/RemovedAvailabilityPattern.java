package pe.ayni.booking.application;

import pe.ayni.booking.domain.model.AvailabilityPattern;

/** A removed weekly availability range and the hours changed by removing it. */
public record RemovedAvailabilityPattern(AvailabilityPattern pattern, HoursAdjustment hours) {}
