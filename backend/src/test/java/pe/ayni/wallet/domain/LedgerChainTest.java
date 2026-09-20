package pe.ayni.wallet.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static pe.ayni.wallet.domain.WalletFixtures.CLOCK;
import static pe.ayni.wallet.domain.WalletFixtures.seedExpiringInDays;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import pe.ayni.shared.domain.Credits;
import pe.ayni.wallet.domain.model.CreditLot;
import pe.ayni.wallet.domain.model.LedgerEntry;
import pe.ayni.wallet.domain.model.LedgerReason;
import pe.ayni.wallet.domain.model.Movement;
import pe.ayni.wallet.domain.model.ReferenceType;
import pe.ayni.wallet.domain.services.LedgerChain;

/**
 * What the chain of hashes is for: noticing that the history was changed.
 *
 * <p>The database already refuses {@code UPDATE} and {@code DELETE} on the ledger. This is about
 * what happens when the change did not go through the database's rules at all: a restored backup,
 * or a superuser who dropped the trigger first.
 */
class LedgerChainTest {

  private static final UUID BOOKING = UUID.randomUUID();

  @Test
  @DisplayName("a ledger nobody touched verifies")
  void anUntouchedLedgerVerifies() {
    assertThat(LedgerChain.isIntact(ledgerOfThreeEntries())).isTrue();
  }

  @Test
  @DisplayName("finds the entry whose contents no longer match its hash")
  void findsAnEntryEditedInPlace() throws Exception {

    List<LedgerEntry> ledger = ledgerOfThreeEntries();
    LedgerEntry second = ledger.get(1);

    // There is no method for this, which is the point: it takes reflection to do to an entry
    // what only a hand on the database could do.
    changeAmountBehindTheApplication(second, 999);

    assertThat(second.matchesOwnHash()).isFalse();
    assertThat(LedgerChain.firstTamperedEntry(ledger)).contains(second);
  }

  @Test
  @DisplayName("finds the break when an entry is replaced by a well formed one")
  void findsAnEntryReplacedByAWellFormedOne() {

    List<LedgerEntry> ledger = ledgerOfThreeEntries();
    LedgerEntry first = ledger.getFirst();
    LedgerEntry third = ledger.get(2);

    // A forger who understands the format: the replacement hashes correctly and points at the
    // entry before it. What it cannot do is match the hash the next entry already carries.
    LedgerEntry forged =
        LedgerEntry.following(
            first, 2, debit(seedExpiringInDays(9, 30), 9), CLOCK.instant());

    assertThat(forged.matchesOwnHash()).isTrue();
    assertThat(LedgerChain.firstTamperedEntry(List.of(first, forged, third))).contains(third);
  }

  @Test
  @DisplayName("finds the break when an entry is removed")
  void findsAnEntryRemoved() {

    List<LedgerEntry> ledger = ledgerOfThreeEntries();

    List<LedgerEntry> withoutTheSecond = List.of(ledger.getFirst(), ledger.get(2));

    assertThat(LedgerChain.firstTamperedEntry(withoutTheSecond)).contains(ledger.get(2));
  }

  @Test
  @DisplayName("verifies whatever order the entries arrive in")
  void verifiesRegardlessOfTheOrderTheyComeBackIn() {

    List<LedgerEntry> ledger = ledgerOfThreeEntries();

    assertThat(LedgerChain.isIntact(List.of(ledger.get(2), ledger.getFirst(), ledger.get(1))))
        .isTrue();
  }

  private static List<LedgerEntry> ledgerOfThreeEntries() {

    CreditLot lot = seedExpiringInDays(10, 30);
    List<LedgerEntry> entries = new ArrayList<>();
    LedgerEntry previous = null;

    for (int sequence = 1; sequence <= 3; sequence++) {
      previous = LedgerEntry.following(previous, sequence, debit(lot, sequence), CLOCK.instant());
      entries.add(previous);
    }
    return entries;
  }

  private static Movement debit(CreditLot lot, int amount) {
    return Movement.debit(
        lot, Credits.of(amount), LedgerReason.BOOKING_CHARGE, ReferenceType.BOOKING, BOOKING);
  }

  private static void changeAmountBehindTheApplication(LedgerEntry entry, int amount)
      throws Exception {
    Field field = LedgerEntry.class.getDeclaredField("amount");
    field.setAccessible(true);
    field.setInt(entry, amount);
  }
}
