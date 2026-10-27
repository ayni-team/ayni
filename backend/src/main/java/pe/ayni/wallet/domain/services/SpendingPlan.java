package pe.ayni.wallet.domain.services;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import pe.ayni.shared.domain.Credits;
import pe.ayni.wallet.InsufficientCreditsException;
import pe.ayni.wallet.domain.model.Allocation;
import pe.ayni.wallet.domain.model.CreditLot;

/**
 * Which groups a charge takes its credits from.
 *
 * <p>The rule is one line and it is the reason this class exists: <b>spend first what expires
 * first</b>. A student holding credits that die in October and credits that never die should not
 * lose the October ones while the others sit untouched.
 *
 * <p>Nothing here touches the database or the clock: it is given the groups, the amount and the
 * moment, and it answers where the credits come from. That is what makes the rule testable without
 * starting Spring.
 */
public record SpendingPlan(List<Allocation> allocations) {

  public SpendingPlan {
    allocations = List.copyOf(allocations);
  }

  /**
   * Works out which groups pay for the given amount.
   *
   * <p>Groups that already expired are not considered, however many credits they still hold: an
   * expired credit is gone even though its row is still there for the history to show.
   *
   * @throws InsufficientCreditsException when the spendable groups do not add up to the amount,
   *     carrying how many credits are missing
   */
  public static SpendingPlan of(Collection<CreditLot> lots, Credits amount, Instant now) {

    List<CreditLot> spendable =
        lots.stream().filter(lot -> lot.isSpendableAt(now)).sorted(CreditLot.EXPIRY_FIRST).toList();

    List<Allocation> allocations = new ArrayList<>();
    Credits outstanding = amount;

    for (CreditLot lot : spendable) {
      if (outstanding.isZero()) {
        break;
      }
      Credits taken = min(lot.remaining(), outstanding);
      allocations.add(new Allocation(lot, taken));
      outstanding = outstanding.minus(taken);
    }

    if (!outstanding.isZero()) {
      // The caller is told how many are missing, not merely that it failed: booking shows the
      // student how many credits short they are.
      throw new InsufficientCreditsException(outstanding);
    }

    return new SpendingPlan(allocations);
  }

  /** The credits this plan spends, which always equals the amount it was asked for. */
  public Credits total() {
    return allocations.stream().map(Allocation::amount).reduce(Credits.ZERO, Credits::plus);
  }

  private static Credits min(Credits one, Credits other) {
    return Comparator.<Credits>naturalOrder().compare(one, other) <= 0 ? one : other;
  }
}
