package pe.ayni.booking.domain.model;

/**
 * The hour cannot be taken: another student holds it, it was booked, it was withdrawn or it has
 * already started.
 *
 * <p>A refusal about the state of the tutor's agenda rather than about the request, which is why it
 * is told apart from a plain {@link BookingRuleViolation}: the student asked for something
 * reasonable and somebody got there first.
 */
public class HourUnavailable extends BookingRuleViolation {

  private static final long serialVersionUID = 1L;

  /** What a student is told when another one confirmed the same hour first. */
  public static final String TAKEN_WHILE_CONFIRMING =
      "This hour was just taken by another student. Nothing was charged; choose another one";

  public HourUnavailable(String message) {
    super(message);
  }

  public HourUnavailable(String message, Throwable cause) {
    super(message, cause);
  }
}
