package pe.ayni.booking.domain.model;

/**
 * A rule of this module refusing what was asked of it.
 *
 * <p>Booking throws this instead of {@code IllegalArgumentException} or {@code
 * IllegalStateException} so that its refusals can be told apart from a programming mistake. The two
 * look identical from outside and deserve opposite answers: a refusal is something the caller can
 * act on, a mistake is something somebody has to fix.
 *
 * <p>A missing argument is not one of these. {@code Objects.requireNonNull} stays where it is,
 * because a null that reached an entity is a bug and not a decision.
 */
public class BookingRuleViolation extends RuntimeException {

  private static final long serialVersionUID = 1L;

  public BookingRuleViolation(String message) {
    super(message);
  }
}
