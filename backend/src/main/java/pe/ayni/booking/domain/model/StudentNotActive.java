package pe.ayni.booking.domain.model;

/**
 * The student asking to book is not an active member of their university: their access is still
 * pending, or it was restricted.
 */
public class StudentNotActive extends BookingRuleViolation {

  private static final long serialVersionUID = 1L;

  public StudentNotActive() {
    super("Your account is not active, so you cannot book tutoring");
  }
}
