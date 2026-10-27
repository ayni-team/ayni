package pe.ayni.booking.domain;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.UUID;
import pe.ayni.booking.domain.model.AvailabilityException;
import pe.ayni.booking.domain.model.AvailabilityPattern;
import pe.ayni.booking.domain.model.AvailabilityPause;
import pe.ayni.booking.domain.model.HourBlock;

/** The bits of a tutor's week that the tests in this package keep needing. */
final class BookingFixtures {

  static final String UPC = "UPC";
  static final UUID TUTOR = UUID.fromString("11111111-1111-4111-8111-111111111111");
  static final ZoneId LIMA = ZoneId.of("America/Lima");
  static final Instant NOW = Instant.parse("2026-09-20T12:00:00Z");

  /** A Monday, so a pattern and a date can be talked about without counting days. */
  static final LocalDate MONDAY = LocalDate.of(2026, 10, 5);

  private BookingFixtures() {}

  /** A weekly window on the day {@link #MONDAY} falls on, valid from a month before and open. */
  static AvailabilityPattern pattern(LocalTime from, LocalTime to) {
    return new AvailabilityPattern(
        UUID.randomUUID(),
        UPC,
        TUTOR,
        MONDAY.getDayOfWeek(),
        from,
        to,
        MONDAY.minusMonths(1),
        null,
        NOW);
  }

  static AvailabilityPattern patternValid(
      LocalTime from, LocalTime to, LocalDate validFrom, LocalDate validUntil) {
    return new AvailabilityPattern(
        UUID.randomUUID(), UPC, TUTOR, MONDAY.getDayOfWeek(), from, to, validFrom, validUntil, NOW);
  }

  static AvailabilityPause pause(LocalDate from, LocalDate to) {
    return new AvailabilityPause(UUID.randomUUID(), UPC, TUTOR, from, to, NOW);
  }

  static AvailabilityException removeWholeDay(LocalDate date) {
    return AvailabilityException.removeWholeDay(UUID.randomUUID(), UPC, TUTOR, date, NOW);
  }

  static AvailabilityException removeWindow(LocalDate date, LocalTime from, LocalTime to) {
    return AvailabilityException.removeSlot(UUID.randomUUID(), UPC, TUTOR, date, from, to, NOW);
  }

  static AvailabilityException addWindow(LocalDate date, LocalTime from, LocalTime to) {
    return AvailabilityException.addSlot(UUID.randomUUID(), UPC, TUTOR, date, from, to, NOW);
  }

  /** The blocks read back as the wall clock times a tutor would recognise. */
  static List<LocalTime> startTimesOf(List<HourBlock> blocks) {
    return blocks.stream()
        .map(block -> ZonedDateTime.ofInstant(block.getStartsAt(), LIMA).toLocalTime())
        .sorted()
        .toList();
  }
}
