package pe.ayni.wallet.application;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.ayni.shared.domain.Credits;
import pe.ayni.shared.tenancy.TenantContext;
import pe.ayni.wallet.ChargeReceipt;
import pe.ayni.wallet.InsufficientCreditsException;
import pe.ayni.wallet.domain.model.Allocation;
import pe.ayni.wallet.domain.model.CreditAccount;
import pe.ayni.wallet.domain.model.CreditLot;
import pe.ayni.wallet.domain.model.LedgerReason;
import pe.ayni.wallet.domain.model.Movement;
import pe.ayni.wallet.domain.model.ReferenceType;
import pe.ayni.wallet.domain.services.SpendingPlan;
import pe.ayni.wallet.infrastructure.CreditLotRepository;

/**
 * Spends a student's credits on a booking.
 *
 * <p>Booking calls this while it is confirming a reservation, and it runs inside booking's
 * transaction: if reserving the hours fails after the charge, the credits come back by themselves
 * because nothing was committed. That is why charging is a call and not an event.
 */
@Service
class ChargeCredits {

  private final Accounts accounts;
  private final CreditLotRepository lots;
  private final LedgerWriter ledger;
  private final Clock clock;

  ChargeCredits(Accounts accounts, CreditLotRepository lots, LedgerWriter ledger, Clock clock) {
    this.accounts = accounts;
    this.lots = lots;
    this.ledger = ledger;
    this.clock = clock;
  }

  /**
   * Takes the credits of a booking, from the groups closest to expiring.
   *
   * @throws InsufficientCreditsException when the spendable groups do not cover the amount. The
   *     exception carries how many credits are missing, so booking can tell the student.
   */
  @Transactional
  ChargeReceipt charge(UUID userId, Credits amount, UUID bookingId) {

    if (amount.isZero()) {
      throw new IllegalArgumentException("A charge of zero credits is not a charge");
    }

    String tenantId = TenantContext.require();
    Instant now = clock.instant();

    // A student with no account has no credits: the answer is the same refusal, with the whole
    // amount missing.
    CreditAccount account =
        accounts.find(userId).orElseThrow(() -> new InsufficientCreditsException(amount));

    // Locked until the transaction ends. Two bookings confirmed at the same instant would
    // otherwise both read the same groups and spend the same credits twice.
    List<CreditLot> spendable = lots.lockSpendable(tenantId, account.id(), now);

    SpendingPlan plan = SpendingPlan.of(spendable, amount, now);

    List<Movement> movements = new ArrayList<>(plan.allocations().size());
    for (Allocation allocation : plan.allocations()) {
      allocation.lot().consume(allocation.amount());
      movements.add(
          Movement.debit(
              allocation.lot(),
              allocation.amount(),
              LedgerReason.BOOKING_CHARGE,
              ReferenceType.BOOKING,
              bookingId));
    }
    // One entry per group, which is what the refund reads to put every credit back where it
    // came from.
    ledger.append(movements);

    return new ChargeReceipt(bookingId, amount);
  }
}
