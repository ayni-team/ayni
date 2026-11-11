package pe.ayni.sessions.domain.model;

/**
 * As many practice rooms are open as the backend keeps at once. A well formed request the state
 * refuses, which is why it answers 409: the room that is left over is not created.
 */
public class PracticeRoomsFull extends SessionRuleViolation {

  private static final long serialVersionUID = 1L;

  public PracticeRoomsFull(int open) {
    super(open + " practice rooms are open, as many as the backend keeps. End one or wait until one"
        + " expires");
  }
}
