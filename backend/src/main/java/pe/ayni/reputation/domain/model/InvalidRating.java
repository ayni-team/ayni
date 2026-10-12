package pe.ayni.reputation.domain.model;

/**
 * The rating lacks what its direction asks for: stars from a student, or the three facts from a
 * tutor. It is the person's request that is wrong, not the state of the session.
 */
public class InvalidRating extends RuntimeException {

  public InvalidRating(String message) {
    super(message);
  }
}
