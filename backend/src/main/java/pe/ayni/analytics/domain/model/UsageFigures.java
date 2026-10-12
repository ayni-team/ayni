package pe.ayni.analytics.domain.model;

/**
 * The numbers a university reads for a day or for a period: tutoring sessions held, hours
 * exchanged, students who took part, students who joined and recognitions granted.
 *
 * <p>Counts and nothing else: no name and no identifier of a person is ever part of an indicator.
 */
public record UsageFigures(
    long sessionsCompleted,
    long hoursExchanged,
    long activeStudents,
    long newStudents,
    long recognitionsGranted) {

  public static final UsageFigures NONE = new UsageFigures(0, 0, 0, 0, 0);
}
