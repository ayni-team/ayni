package pe.ayni.sessions.domain.model;

/**
 * Somebody who is neither the student nor the tutor of a session asked for it.
 *
 * <p>Refused whatever they hold, a link included: the backend guide says only participants may read
 * a session, and the room name is what lets anyone into the call.
 */
public class NotAParticipant extends SessionRuleViolation {

  private static final long serialVersionUID = 1L;

  public NotAParticipant() {
    super("Only the student and the tutor of this session can see it or join it");
  }
}
