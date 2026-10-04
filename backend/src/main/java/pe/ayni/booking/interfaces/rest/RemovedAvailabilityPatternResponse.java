package pe.ayni.booking.interfaces.rest;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;
import pe.ayni.booking.application.RemovedAvailabilityPattern;
import pe.ayni.booking.domain.model.AvailabilityPattern;

/** A removed weekly range and the generated hours that changed with it. */
@Schema(name = "RemovedAvailabilityPattern")
public record RemovedAvailabilityPatternResponse(
    UUID patternId,
    DayOfWeek dayOfWeek,
    LocalTime startsAtTime,
    LocalTime endsAtTime,
    LocalDate validFrom,
    LocalDate validUntil,
    int withdrawnHours,
    int bookedHoursKept,
    String notice) {

  static RemovedAvailabilityPatternResponse of(RemovedAvailabilityPattern removed) {
    AvailabilityPattern pattern = removed.pattern();
    return new RemovedAvailabilityPatternResponse(
        pattern.getId(),
        pattern.getDayOfWeek(),
        pattern.getStartsAtTime(),
        pattern.getEndsAtTime(),
        pattern.getValidFrom(),
        pattern.getValidUntil(),
        removed.hours().withdrawal().withdrawn(),
        removed.hours().withdrawal().bookedKept(),
        HoursNotice.of(removed.hours().withdrawal()));
  }
}
