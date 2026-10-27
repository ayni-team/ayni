package pe.ayni.wallet.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static pe.ayni.wallet.domain.WalletFixtures.CLOCK;
import static pe.ayni.wallet.domain.WalletFixtures.seedExpiringInDays;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import pe.ayni.shared.domain.Credits;
import pe.ayni.wallet.domain.model.Allocation;
import pe.ayni.wallet.domain.model.CreditLot;
import pe.ayni.wallet.domain.model.LedgerEntry;
import pe.ayni.wallet.domain.model.LedgerReason;
import pe.ayni.wallet.domain.model.Movement;
import pe.ayni.wallet.domain.model.ReferenceType;
import pe.ayni.wallet.domain.services.RefundPlan;

/** Where a refund puts the credits back. */
class RefundPlanTest {

  private static final UUID BOOKING = UUID.randomUUID();

  @Test
  @DisplayName("returns the credits to the group they were taken from")
  void returnsCreditsToTheirOwnGroup() {

    CreditLot october = seedExpiringInDays(5, 10);
    CreditLot december = seedExpiringInDays(5, 90);
    october.consume(Credits.of(5));
    december.consume(Credits.of(1));

    List<LedgerEntry> charge =
        chain(
            debit(october, 5, LedgerReason.BOOKING_CHARGE),
            debit(december, 1, LedgerReason.BOOKING_CHARGE));

    RefundPlan plan = RefundPlan.of(charge, byId(october, december));

    assertThat(plan.allocations())
        .extracting(Allocation::lot, Allocation::amount)
        .containsExactly(
            tuple(october, Credits.of(5)), tuple(december, Credits.of(1)));
  }

  @Test
  @DisplayName("the refunded credits keep the expiry they had")
  void refundedCreditsKeepTheirExpiry() {

    CreditLot october = seedExpiringInDays(5, 10);
    october.consume(Credits.of(2));

    RefundPlan plan =
        RefundPlan.of(chain(debit(october, 2, LedgerReason.BOOKING_CHARGE)), byId(october));
    plan.allocations().forEach(allocation -> allocation.lot().restore(allocation.amount()));

    // The same group, with the same date: booking and cancelling is not a way of renewing
    // credits that were about to die.
    assertThat(october.remaining()).isEqualTo(Credits.of(5));
    assertThat(october.expiresAt()).isEqualTo(seedExpiringInDays(5, 10).expiresAt());
    assertThat(october.isSpendableAt(CLOCK.instant())).isTrue();
  }

  @Test
  @DisplayName("credits go back even when the group they came from has expired in the meantime")
  void refundsIntoAGroupThatDiedInTheMeantime() {

    CreditLot dying = seedExpiringInDays(4, 10);
    dying.consume(Credits.of(4));

    RefundPlan plan =
        RefundPlan.of(chain(debit(dying, 4, LedgerReason.BOOKING_CHARGE)), byId(dying));
    plan.allocations().forEach(allocation -> allocation.lot().restore(allocation.amount()));

    // Back where they came from, which after the expiry date means back to credits that are
    // already gone. The history shows both the refund and the expiry.
    assertThat(dying.remaining()).isEqualTo(Credits.of(4));
    assertThat(dying.isSpendableAt(dying.expiresAt().plusSeconds(1))).isFalse();
  }

  @Test
  @DisplayName("pays nothing the second time a cancellation arrives")
  void refundsOnlyOnce() {

    CreditLot october = seedExpiringInDays(5, 10);
    october.consume(Credits.of(3));

    List<LedgerEntry> history =
        chain(
            debit(october, 3, LedgerReason.BOOKING_CHARGE),
            credit(october, 3, LedgerReason.BOOKING_REFUND));

    RefundPlan plan = RefundPlan.of(history, byId(october));

    assertThat(plan.isEmpty()).isTrue();
    assertThat(plan.total()).isEqualTo(Credits.ZERO);
  }

  @Test
  @DisplayName("a booking that was never charged owes nothing")
  void refundsNothingForABookingThatWasNeverCharged() {
    assertThat(RefundPlan.of(List.of(), Map.of()).isEmpty()).isTrue();
  }

  private static Movement debit(CreditLot lot, int amount, LedgerReason reason) {
    return Movement.debit(lot, Credits.of(amount), reason, ReferenceType.BOOKING, BOOKING);
  }

  private static Movement credit(CreditLot lot, int amount, LedgerReason reason) {
    return Movement.credit(lot, Credits.of(amount), reason, ReferenceType.BOOKING, BOOKING);
  }

  /** Writes the movements down as the ledger would, one after the other. */
  private static List<LedgerEntry> chain(Movement... movements) {
    List<LedgerEntry> entries = new ArrayList<>();
    LedgerEntry previous = null;
    long sequence = 1;
    for (Movement movement : movements) {
      previous = LedgerEntry.following(previous, sequence++, movement, CLOCK.instant());
      entries.add(previous);
    }
    return entries;
  }

  private static Map<UUID, CreditLot> byId(CreditLot... lots) {
    return List.of(lots).stream().collect(Collectors.toMap(CreditLot::id, Function.identity()));
  }
}
