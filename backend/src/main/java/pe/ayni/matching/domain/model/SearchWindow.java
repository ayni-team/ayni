package pe.ayni.matching.domain.model;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/**
 * The hours a student said they can make: from the first instant included to the last excluded.
 *
 * <p>Half open, like every range of hours in the application, so two windows laid end to end never
 * both claim the hour on the boundary.
 */
public record SearchWindow(Instant from, Instant to) {

  /** @throws MatchingRuleViolation when the window does not end after it starts */
  public SearchWindow {
    Objects.requireNonNull(from, "from must not be null");
    Objects.requireNonNull(to, "to must not be null");
    if (!from.isBefore(to)) {
      throw new MatchingRuleViolation("from must be before to");
    }
  }

  /**
   * How far an hour starting at {@code startsAt} is from this window: zero inside it, otherwise the
   * distance to the edge it fell outside of. That is what "closest" means to a student who asked
   * for Tuesday afternoon, rather than the distance to some arbitrary point of the window.
   */
  public Duration distanceTo(Instant startsAt) {
    if (startsAt.isBefore(from)) {
      return Duration.between(startsAt, from);
    }
    if (!startsAt.isBefore(to)) {
      return Duration.between(to, startsAt);
    }
    return Duration.ZERO;
  }
}
