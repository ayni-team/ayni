package pe.ayni.analytics.domain.model;

/**
 * The projection could not tell which day an event belongs to, because the university it names has
 * no time zone to read: it is not known, or nobody can say.
 */
public class UnknownUniversity extends RuntimeException {

  public UnknownUniversity() {
    super("The time zone of the university is not known");
  }
}
