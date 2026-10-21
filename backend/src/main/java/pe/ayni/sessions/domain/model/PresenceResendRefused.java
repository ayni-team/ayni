package pe.ayni.sessions.domain.model;

/**
 * A presence code cannot be sent again right now: presence is already confirmed, its attempts are
 * spent, the resends of the session are used up, or the last one was too recent.
 *
 * <p>A well formed request the state of the check refuses, so it answers 409, the same as any other
 * refusal of the presence check.
 */
public class PresenceResendRefused extends SessionRuleViolation {

  private static final long serialVersionUID = 1L;

  public PresenceResendRefused(String message) {
    super(message);
  }
}
