package pe.ayni.booking.domain.model;

/**
 * The tutor does not offer the hours asked for as one consecutive stretch.
 *
 * <p>A booking covers consecutive hours with the same tutor. Monday at five and Wednesday at seven
 * are two bookings, and so is a request whose second hour the tutor never published.
 */
public class HoursNotOffered extends BookingRuleViolation {

  private static final long serialVersionUID = 1L;

  public HoursNotOffered(String message) {
    super(message);
  }
}
