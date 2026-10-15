package pe.ayni.booking.domain.model;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.Objects;

/**
 * The days a tutor reads their agenda for (US21), in the university's own zone: from the first
 * midnight to the midnight after the last day, both days included.
 *
 * <p>The days are the university's because that is where the tutor lives their week: an hour at
 * 22:00 in Lima is on that day, although it is the next one in UTC.
 */
public record AgendaPeriod(LocalDate from, LocalDate to, ZoneId zone) {

  /** The longest agenda that can be read at once, so one answer stays a page of a calendar. */
  public static final int MAX_DAYS = 31;

  /** One week, today included, when the tutor names no day. */
  public static final int DEFAULT_DAYS = 7;

  public AgendaPeriod {
    Objects.requireNonNull(from, "from must not be null");
    Objects.requireNonNull(to, "to must not be null");
    Objects.requireNonNull(zone, "zone must not be null");
    if (to.isBefore(from)) {
      throw new BookingRuleViolation("to must not be before from");
    }
    if (ChronoUnit.DAYS.between(from, to) + 1 > MAX_DAYS) {
      throw new BookingRuleViolation("The agenda covers at most " + MAX_DAYS + " days at once");
    }
  }

  /**
   * The period a request asks for. A missing {@code from} is today in the university's zone, and a
   * missing {@code to} the last day of a week from {@code from}.
   */
  public static AgendaPeriod requested(LocalDate from, LocalDate to, ZoneId zone, Instant now) {
    LocalDate first = from != null ? from : LocalDate.ofInstant(now, zone);
    LocalDate last = to != null ? to : first.plusDays(DEFAULT_DAYS - 1);
    return new AgendaPeriod(first, last, zone);
  }

  /** The first instant of the period: midnight of {@code from} in the zone. */
  public Instant start() {
    return from.atStartOfDay(zone).toInstant();
  }

  /** The instant the period ends, excluded: midnight after {@code to} in the zone. */
  public Instant end() {
    return to.plusDays(1).atStartOfDay(zone).toInstant();
  }
}
