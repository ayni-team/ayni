package pe.ayni.wallet.domain.services;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import pe.ayni.shared.domain.Credits;
import pe.ayni.wallet.domain.model.Allocation;
import pe.ayni.wallet.domain.model.CreditLot;
import pe.ayni.wallet.domain.model.LedgerEntry;
import pe.ayni.wallet.domain.model.LedgerReason;

/**
 * Which groups a refund returns credits to.
 *
 * <p>A refund is not "add the credits back". The credits go home: to the same groups they were
 * taken from, with the expiry those groups had. Otherwise cancelling a booking would quietly
 * launder credits that were about to die into credits that live longer, and a student could keep
 * an expiring grant alive by booking and cancelling.
 *
 * <p>The ledger is what remembers where they came from, which is the whole reason a charge writes
 * one entry per group instead of a single total.
 */
public record RefundPlan(List<Allocation> allocations) {

  public RefundPlan {
    allocations = List.copyOf(allocations);
  }

  /**
   * Works out what a booking owes back, from the entries it left in the ledger.
   *
   * <p>Refunds already given are subtracted, so refunding twice returns nothing the second time.
   * Cancelling is announced by an event, and an event that arrives twice must not pay twice.
   *
   * @param bookingEntries every ledger entry that points at the booking
   * @param lotsById the groups those entries touched
   * @throws IllegalStateException when an entry points at a group that is not there, which would
   *     mean the ledger and the groups disagree
   */
  public static RefundPlan of(List<LedgerEntry> bookingEntries, Map<UUID, CreditLot> lotsById) {

    // Insertion ordered, so the refund entries come out in the order the charge was made.
    Map<UUID, Credits> owedByLot = new LinkedHashMap<>();

    for (LedgerEntry entry : bookingEntries) {
      if (entry.reason() == LedgerReason.BOOKING_CHARGE) {
        owedByLot.merge(entry.lotId(), entry.amount(), Credits::plus);
      }
    }
    for (LedgerEntry entry : bookingEntries) {
      if (entry.reason() == LedgerReason.BOOKING_REFUND) {
        Credits owed = owedByLot.getOrDefault(entry.lotId(), Credits.ZERO);
        owedByLot.put(entry.lotId(), subtractAtMost(owed, entry.amount()));
      }
    }

    List<Allocation> allocations = new ArrayList<>();
    owedByLot.forEach(
        (lotId, owed) -> {
          if (owed.isZero()) {
            return;
          }
          CreditLot lot = lotsById.get(lotId);
          if (lot == null) {
            throw new IllegalStateException(
                "The ledger refers to the group " + lotId + ", which does not exist");
          }
          allocations.add(new Allocation(lot, owed));
        });

    return new RefundPlan(allocations);
  }

  /** Whether there is nothing left to return, because it was refunded already. */
  public boolean isEmpty() {
    return allocations.isEmpty();
  }

  /** The credits this plan returns. */
  public Credits total() {
    return allocations.stream().map(Allocation::amount).reduce(Credits.ZERO, Credits::plus);
  }

  private static Credits subtractAtMost(Credits owed, Credits refunded) {
    return refunded.isGreaterThanOrEqualTo(owed) ? Credits.ZERO : owed.minus(refunded);
  }
}
