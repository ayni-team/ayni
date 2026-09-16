package pe.ayni.shared.domain;

import java.util.Objects;

/**
 * A whole amount of credits. One credit represents one hour of tutoring.
 *
 * <p>It is a type of its own rather than an {@code int} so that minutes, credits and money cannot
 * be passed in place of one another by mistake: the compiler catches it.
 *
 * <p>Credits are never fractional, so the amount is an {@code int} and not a decimal. Instances are
 * immutable, and the constructor rejects negatives, so an invalid amount cannot exist anywhere in
 * the program.
 */
public record Credits(int amount) implements Comparable<Credits> {

  public static final Credits ZERO = new Credits(0);

  public Credits {
    if (amount < 0) {
      throw new IllegalArgumentException("Credits cannot be negative: " + amount);
    }
  }

  public static Credits of(int amount) {
    return new Credits(amount);
  }

  public Credits plus(Credits other) {
    return new Credits(this.amount + other.amount);
  }

  /**
   * Subtracts the given amount.
   *
   * @throws IllegalArgumentException when the result would be negative
   */
  public Credits minus(Credits other) {
    if (other.amount > this.amount) {
      throw new IllegalArgumentException(
          "Cannot subtract " + other.amount + " credits from " + this.amount);
    }
    return new Credits(this.amount - other.amount);
  }

  public boolean isZero() {
    return amount == 0;
  }

  /** Reads as the business rule it represents: whether a balance covers a price. */
  public boolean isGreaterThanOrEqualTo(Credits other) {
    return this.amount >= other.amount;
  }

  @Override
  public int compareTo(Credits other) {
    return Integer.compare(this.amount, Objects.requireNonNull(other).amount);
  }
}
