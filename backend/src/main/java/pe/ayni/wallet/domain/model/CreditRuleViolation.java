package pe.ayni.wallet.domain.model;

/**
 * A rule of this module refusing what was asked of it.
 *
 * <p>Wallet throws this instead of {@code IllegalArgumentException} so that its refusals can be
 * told apart from a programming mistake. The two look identical from outside and deserve opposite
 * answers: a refusal is a 400 the caller can act on, a mistake is a 500 somebody has to fix.
 *
 * <p>{@code InsufficientCreditsException} is not one of these. It sits at the module root because
 * booking catches it by name and shows the student how many credits they are short.
 */
public class CreditRuleViolation extends RuntimeException {

  private static final long serialVersionUID = 1L;

  public CreditRuleViolation(String message) {
    super(message);
  }
}
