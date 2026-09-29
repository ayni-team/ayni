package pe.ayni.sessions.domain.model;

/**
 * Presence cannot be confirmed right now, whatever code is typed: no code has been issued yet, it
 * expired, its attempts are spent, the participant never joined, or the session is no longer in
 * progress.
 *
 * <p>A well formed request the state of the check refuses, which is why it answers 409 and not 400.
 */
public class PresenceCheckUnavailable extends SessionRuleViolation {

  private static final long serialVersionUID = 1L;

  public PresenceCheckUnavailable(String message) {
    super(message);
  }
}
