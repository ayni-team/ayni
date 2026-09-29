package pe.ayni.sessions.domain.model;

/**
 * A participant tried to join when the room is not open: too early, already over, or a session that
 * will not take place.
 *
 * <p>A well formed request the state of the session refuses, which is why it answers 409 and not
 * 400.
 */
public class SessionNotOpen extends SessionRuleViolation {

  private static final long serialVersionUID = 1L;

  public SessionNotOpen(String message) {
    super(message);
  }
}
