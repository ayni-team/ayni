package pe.ayni.analytics.interfaces.rest;

import pe.ayni.analytics.domain.model.UsageFigures;
import pe.ayni.analytics.domain.model.UsageIndicators;

/**
 * The indicators of a period as the file a coordinator takes to a meeting: a header, one line per
 * day and a last line with the totals of the period.
 *
 * <p>Only dates and numbers, so nothing needs quoting and nothing a person typed can turn into a
 * formula when the file is opened in a spreadsheet.
 */
final class IndicatorsCsv {

  static final String HEADER =
      "day,sessions_completed,hours_exchanged,active_students,new_students,recognitions_granted";

  private IndicatorsCsv() {}

  static String of(UsageIndicators indicators) {
    StringBuilder csv = new StringBuilder(HEADER).append("\r\n");
    indicators.days().forEach(day -> line(csv, day.day().toString(), day.figures()));
    line(csv, "TOTAL", indicators.totals());
    return csv.toString();
  }

  private static void line(StringBuilder csv, String label, UsageFigures figures) {
    csv.append(label)
        .append(',')
        .append(figures.sessionsCompleted())
        .append(',')
        .append(figures.hoursExchanged())
        .append(',')
        .append(figures.activeStudents())
        .append(',')
        .append(figures.newStudents())
        .append(',')
        .append(figures.recognitionsGranted())
        .append("\r\n");
  }
}
