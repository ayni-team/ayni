package pe.ayni.analytics.domain.model;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.stream.Stream;

/**
 * The days a coordinator asks about, both ends included.
 *
 * <p>A period that ends before it starts, or that covers more than {@link #MAX_DAYS} days, is
 * refused: the answer carries one row per day, and a request is not an export of the whole history.
 */
public record IndicatorPeriod(LocalDate from, LocalDate to) {

  /** A leap year: the longest period a single request may cover. */
  public static final int MAX_DAYS = 366;

  public IndicatorPeriod {
    if (from == null || to == null) {
      throw new InvalidPeriod("The period needs a start and an end");
    }
    if (to.isBefore(from)) {
      throw new InvalidPeriod("The period ends before it starts");
    }
    if (ChronoUnit.DAYS.between(from, to) + 1 > MAX_DAYS) {
      throw new InvalidPeriod("The period covers more than " + MAX_DAYS + " days");
    }
  }

  /** Every day of the period, in order. */
  public List<LocalDate> days() {
    return Stream.iterate(from, day -> !day.isAfter(to), day -> day.plusDays(1)).toList();
  }
}
