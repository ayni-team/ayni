package pe.ayni.sessions.domain.model;

/**
 * A participant typed a presence code that is not the one they were sent.
 *
 * <p>The attempt counts, so the message says how many are left: the participant needs to know
 * before the last one is spent.
 */
public class WrongPresenceCode extends SessionRuleViolation {

  private static final long serialVersionUID = 1L;

  private final int attemptsLeft;

  public WrongPresenceCode(int attemptsLeft) {
    super(attemptsLeft == 0
        ? "That is not the code you were sent, and it can no longer be used"
        : "That is not the code you were sent. Attempts left: " + attemptsLeft);
    this.attemptsLeft = attemptsLeft;
  }

  public int attemptsLeft() {
    return attemptsLeft;
  }
}
