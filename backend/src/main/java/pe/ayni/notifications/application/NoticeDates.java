package pe.ayni.notifications.application;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * How a date reads in an email: in Spanish, and in the zone of the university, which the text
 * names so that nobody has to guess it.
 */
final class NoticeDates {

  private static final Locale SPANISH = Locale.forLanguageTag("es-PE");
  private static final DateTimeFormatter DAY =
      DateTimeFormatter.ofPattern("EEEE d 'de' MMMM 'de' yyyy", SPANISH);
  private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm", SPANISH);
  private static final DateTimeFormatter ZONE = DateTimeFormatter.ofPattern("VV", SPANISH);

  private NoticeDates() {}

  /** The day alone: the weekday, the day, the month and the year, all in words. */
  static String day(Instant at, ZoneId zone) {
    return DAY.format(at.atZone(zone));
  }

  /** The day and the hours a tutoring takes, followed by the zone that gives them meaning. */
  static String span(Instant from, Instant until, ZoneId zone) {
    return "%s, de %s a %s (%s)"
        .formatted(
            DAY.format(from.atZone(zone)),
            TIME.format(from.atZone(zone)),
            TIME.format(until.atZone(zone)),
            ZONE.format(from.atZone(zone)));
  }
}
