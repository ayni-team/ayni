package pe.ayni.wallet.application;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.ayni.shared.tenancy.TenantContext;
import pe.ayni.wallet.domain.model.Allocation;
import pe.ayni.wallet.domain.model.CreditLot;
import pe.ayni.wallet.domain.model.LedgerEntry;
import pe.ayni.wallet.domain.model.LedgerReason;
import pe.ayni.wallet.domain.model.Movement;
import pe.ayni.wallet.domain.model.ReferenceType;
import pe.ayni.wallet.domain.services.RefundPlan;
import pe.ayni.wallet.infrastructure.CreditLotRepository;
import pe.ayni.wallet.infrastructure.LedgerEntryRepository;

/**
 * Returns the credits of a cancelled booking.
 *
 * <p>The credits go back to the groups they were taken from, with the expiry those groups had. A
 * booking cancelled the day before an initial grant dies gives back credits that die the next day,
 * not fresh ones: otherwise booking and cancelling would be a way of renewing credits that were
 * about to expire.
 *
 * <p>Cancelling always refunds, so this runs for every cancellation, including the ones booking
 * records against whoever cancelled late.
 */
@Service
class RefundBooking {

  private final CreditLotRepository lots;
  private final LedgerEntryRepository entries;
  private final LedgerWriter ledger;

  RefundBooking(CreditLotRepository lots, LedgerEntryRepository entries, LedgerWriter ledger) {
    this.lots = lots;
    this.entries = entries;
    this.ledger = ledger;
  }

  /**
   * Gives back what a booking took.
   *
   * <p>Does nothing when the booking was never charged, and nothing again when it was already
   * refunded: cancellation arrives as an event, and an event that is delivered twice must not pay
   * twice.
   */
  @Transactional
  void refund(UUID bookingId) {

    String tenantId = TenantContext.require();

    List<LedgerEntry> bookingEntries =
        entries.findByTenantIdAndReferenceTypeAndReferenceIdOrderBySequenceNumberAsc(
            tenantId, ReferenceType.BOOKING, bookingId);
    if (bookingEntries.isEmpty()) {
      return;
    }

    Set<UUID> touchedLots =
        bookingEntries.stream()
            .map(LedgerEntry::lotId)
            .filter(Objects::nonNull)
            .collect(Collectors.toSet());

    Map<UUID, CreditLot> lotsById =
        lots.lockByIds(tenantId, touchedLots).stream()
            .collect(Collectors.toMap(CreditLot::id, Function.identity()));

    RefundPlan plan = RefundPlan.of(bookingEntries, lotsById);
    if (plan.isEmpty()) {
      return;
    }

    List<Movement> movements = new ArrayList<>(plan.allocations().size());
    for (Allocation allocation : plan.allocations()) {
      allocation.lot().restore(allocation.amount());
      movements.add(
          Movement.credit(
              allocation.lot(),
              allocation.amount(),
              LedgerReason.BOOKING_REFUND,
              ReferenceType.BOOKING,
              bookingId));
    }
    ledger.append(movements);
  }
}
