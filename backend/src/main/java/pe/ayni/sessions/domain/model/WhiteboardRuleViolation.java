package pe.ayni.sessions.domain.model;

/** An operation on a whiteboard that is malformed or would make it too big: answered 400. */
public class WhiteboardRuleViolation extends SessionRuleViolation {

  private static final long serialVersionUID = 1L;

  public WhiteboardRuleViolation(String message) {
    super(message);
  }
}
