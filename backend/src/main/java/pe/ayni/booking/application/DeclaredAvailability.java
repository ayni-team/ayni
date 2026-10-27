package pe.ayni.booking.application;

import pe.ayni.booking.domain.model.AvailabilityPattern;

/** A weekly window just declared, and the hours its declaration generated. */
public record DeclaredAvailability(AvailabilityPattern pattern, HoursGeneration generation) {}
