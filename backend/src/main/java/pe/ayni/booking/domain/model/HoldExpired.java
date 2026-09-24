package pe.ayni.booking.domain.model;

/**
 * The student is not holding the hour they are trying to confirm: their hold ran out, or they never
 * took one.
 *
 * <p>Confirming requires a live hold because the hold is what guarantees the student that nobody
 * else took the hour while they were writing. Once it is gone that guarantee is gone too, and the
 * honest answer is to choose the hour again.
 */
public class HoldExpired extends BookingRuleViolation {

  private static final long serialVersionUID = 1L;

  public HoldExpired(String message) {
    super(message);
  }
}
