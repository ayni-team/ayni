package pe.ayni.sessions.domain.model;

/**
 * A rule of this module refusing what was asked of it, told apart from a programming mistake the
 * same way the other modules do it.
 */
public class SessionRuleViolation extends RuntimeException {

  private static final long serialVersionUID = 1L;

  public SessionRuleViolation(String message) {
    super(message);
  }
}
