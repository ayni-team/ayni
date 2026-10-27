package pe.ayni.matching.domain.model;

/**
 * A rule of this module refusing what was asked of it.
 *
 * <p>Thrown instead of {@code IllegalArgumentException} so that a search the student can correct,
 * such as a range that ends before it starts, is told apart from a programming mistake and answered
 * with a 400 rather than a 500.
 */
public class MatchingRuleViolation extends RuntimeException {

  private static final long serialVersionUID = 1L;

  public MatchingRuleViolation(String message) {
    super(message);
  }
}
