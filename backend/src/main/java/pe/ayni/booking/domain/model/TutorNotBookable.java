package pe.ayni.booking.domain.model;

/**
 * The tutor cannot be booked for that subject: they are not an active member of the university, or
 * they are not enabled to teach it there.
 *
 * <p>The first step of the confirmation, before anything is charged. A tutor can withdraw a skill or
 * lose it between the search and the confirmation, and the search projection may not know yet.
 */
public class TutorNotBookable extends BookingRuleViolation {

  private static final long serialVersionUID = 1L;

  public TutorNotBookable() {
    super("This tutor is not enabled to teach this subject");
  }
}
