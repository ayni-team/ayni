package pe.ayni.booking.domain.services;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import pe.ayni.booking.domain.model.AvailabilityException;
import pe.ayni.booking.domain.model.AvailabilityPattern;
import pe.ayni.booking.domain.model.AvailabilityPause;
import pe.ayni.booking.domain.model.BookingRuleViolation;
import pe.ayni.booking.domain.model.ExceptionKind;
import pe.ayni.booking.domain.model.HourBlock;

/**
 * Turns what a tutor says about their week into the hours a student can actually book.
 *
 * <p>Three things decide it, in this order: a pause removes whole days, a whole day REMOVE removes
 * one more, and what is left is the weekly patterns for that weekday, minus the windows a REMOVE
 * took away, plus the hours an ADD put in.
 *
 * <p>Nothing here touches the database or the clock: it is given the rules, the range and the
 * moment, and it answers with the blocks. That is what makes it testable without starting Spring.
 */
public class BlockGenerator {

  private static final int MINUTES_IN_AN_HOUR = 60;

  /**
   * The blocks a tutor's rules produce between two dates, both included.
   *
   * @param zone the calendar the tutor's times are written in
   * @param now what to stamp the blocks with
   * @throws BookingRuleViolation when the range ends before it starts
   */
  public List<HourBlock> generate(
      List<AvailabilityPattern> patterns,
      List<AvailabilityException> exceptions,
      List<AvailabilityPause> pauses,
      LocalDate from,
      LocalDate to,
      ZoneId zone,
      Instant now) {

    Objects.requireNonNull(patterns, "patterns must not be null");
    Objects.requireNonNull(exceptions, "exceptions must not be null");
    Objects.requireNonNull(pauses, "pauses must not be null");
    Objects.requireNonNull(from, "from date must not be null");
    Objects.requireNonNull(to, "to date must not be null");
    Objects.requireNonNull(zone, "zone must not be null");
    Objects.requireNonNull(now, "now must not be null");

    if (to.isBefore(from)) {
      throw new BookingRuleViolation("to date must be on or after from date");
    }

    // Keyed by what the table is unique on, so an hour described twice is still one hour.
    Map<BlockKey, HourBlock> blocks = new LinkedHashMap<>();

    for (LocalDate date = from; !date.isAfter(to); date = date.plusDays(1)) {
      final LocalDate day = date;

      if (pauses.stream().anyMatch(pause -> pause.includes(day))) {
        continue;
      }

      List<AvailabilityException> thatDay =
          exceptions.stream().filter(e -> e.getExceptionDate().equals(day)).toList();

      if (thatDay.stream().anyMatch(AvailabilityException::removesTheWholeDay)) {
        continue;
      }

      List<AvailabilityPattern> active =
          patterns.stream()
              .filter(pattern -> pattern.getDayOfWeek() == day.getDayOfWeek())
              .filter(pattern -> !day.isBefore(pattern.getValidFrom()))
              .filter(
                  pattern ->
                      pattern.getValidUntil() == null || !day.isAfter(pattern.getValidUntil()))
              .toList();

      // Patterns first: when a pattern and an ADD describe the same hour, the hour exists once and
      // keeps the pattern it came from, so it stays traceable to the rule that created it.
      for (AvailabilityPattern pattern : active) {
        for (int minute : wholeHoursBetween(pattern.getStartsAtTime(), pattern.getEndsAtTime())) {
          if (isRemoved(thatDay, minute)) {
            continue;
          }
          collect(
              blocks, day, minute, zone, pattern.getTenantId(), pattern.getTutorId(),
              pattern.getId(), now);
        }
      }

      for (AvailabilityException added : thatDay) {
        if (added.getKind() != ExceptionKind.ADD) {
          continue;
        }
        for (int minute : wholeHoursBetween(added.getStartsAtTime(), added.getEndsAtTime())) {
          collect(
              blocks, day, minute, zone, added.getTenantId(), added.getTutorId(), null, now);
        }
      }
    }

    return List.copyOf(blocks.values());
  }

  /**
   * The whole hours that fit inside a window, as minutes past midnight.
   *
   * <p>Minutes rather than {@link LocalTime} arithmetic, and that is not a matter of taste. {@code
   * LocalTime.plusHours} wraps around midnight, so a window ending at 23:00 never reached its end
   * and the loop that walked it never finished.
   */
  private static List<Integer> wholeHoursBetween(LocalTime from, LocalTime to) {
    List<Integer> starts = new ArrayList<>();
    int lastMinute = minutesOf(to);
    for (int minute = minutesOf(from);
        minute + MINUTES_IN_AN_HOUR <= lastMinute;
        minute += MINUTES_IN_AN_HOUR) {
      starts.add(minute);
    }
    return starts;
  }

  /**
   * Whether a REMOVE of that date takes this hour away.
   *
   * <p>Whole day removals were already dealt with before we got here, so only the timed ones are
   * asked, and each one answers for itself.
   */
  private static boolean isRemoved(List<AvailabilityException> thatDay, int blockStartMinute) {
    return thatDay.stream()
        .filter(e -> e.getKind() == ExceptionKind.REMOVE && e.coversAWindow())
        .anyMatch(e -> e.affects(blockStartMinute, blockStartMinute + MINUTES_IN_AN_HOUR));
  }

  private static void collect(
      Map<BlockKey, HourBlock> blocks,
      LocalDate day,
      int minute,
      ZoneId zone,
      String tenantId,
      UUID tutorId,
      UUID patternId,
      Instant now) {

    ZonedDateTime start =
        ZonedDateTime.of(day, LocalTime.of(minute / MINUTES_IN_AN_HOUR, minute % MINUTES_IN_AN_HOUR), zone);
    Instant startsAt = start.toInstant();
    // plusHours moves the instant rather than the wall clock, so the block is one hour long even
    // on the night the clocks change, which is what "one credit, one hour" has to mean.
    Instant endsAt = start.plusHours(1).toInstant();

    blocks.computeIfAbsent(
        new BlockKey(tenantId, tutorId, startsAt),
        key ->
            new HourBlock(UUID.randomUUID(), tenantId, tutorId, startsAt, endsAt, patternId, now));
  }

  private static int minutesOf(LocalTime time) {
    return time.getHour() * MINUTES_IN_AN_HOUR + time.getMinute();
  }

  /** What {@code uq_hour_blocks_tenant_tutor_start} is unique on. */
  private record BlockKey(String tenantId, UUID tutorId, Instant startsAt) {}
}
