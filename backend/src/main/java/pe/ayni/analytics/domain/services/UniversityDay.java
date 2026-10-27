package pe.ayni.analytics.domain.services;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

/**
 * Which day an instant belongs to for a university.
 *
 * <p>The day is the one of the university's own time zone, not UTC: a session that ends at 22:00 in
 * Lima is already the next day in UTC, and the coordinator's calendar is the one of their campus.
 */
public final class UniversityDay {

  private UniversityDay() {}

  public static LocalDate of(Instant moment, ZoneId zone) {
    return moment.atZone(zone).toLocalDate();
  }
}
