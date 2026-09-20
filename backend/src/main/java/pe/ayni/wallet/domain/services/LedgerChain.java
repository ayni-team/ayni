package pe.ayni.wallet.domain.services;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import pe.ayni.wallet.domain.model.LedgerEntry;

/**
 * Checks that a university's ledger is still the one the application wrote.
 *
 * <p>Two things are verified for every entry: that it matches its own hash, and that it carries the
 * hash of the entry before it. The first catches a row edited in place; the second catches a row
 * removed, inserted or reordered. A trigger already refuses {@code UPDATE} and {@code DELETE}, so
 * this is what remains for the cases the trigger cannot see: a restore from a doctored backup, or a
 * superuser who dropped the trigger first.
 */
public final class LedgerChain {

  private LedgerChain() {}

  /**
   * Returns the first entry that does not add up, if there is one.
   *
   * <p>The entries are verified in sequence order, so the answer is the point where the history
   * stopped being trustworthy, not merely that something somewhere is wrong.
   */
  public static Optional<LedgerEntry> firstTamperedEntry(List<LedgerEntry> entries) {

    List<LedgerEntry> inOrder =
        entries.stream().sorted(Comparator.comparingLong(LedgerEntry::sequenceNumber)).toList();

    LedgerEntry previous = null;
    for (LedgerEntry entry : inOrder) {
      if (!entry.matchesOwnHash() || !entry.follows(previous)) {
        return Optional.of(entry);
      }
      previous = entry;
    }
    return Optional.empty();
  }

  /** Whether the whole chain holds. */
  public static boolean isIntact(List<LedgerEntry> entries) {
    return firstTamperedEntry(entries).isEmpty();
  }
}
