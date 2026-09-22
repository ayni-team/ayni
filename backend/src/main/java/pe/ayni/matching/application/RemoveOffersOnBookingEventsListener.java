package pe.ayni.matching.application;

import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;
import pe.ayni.matching.infrastructure.AvailableOfferRepository;
import pe.ayni.shared.events.BookingConfirmed;
import pe.ayni.shared.events.HoursWithdrawn;

/**
 * Keeps the search projection free of hours that can no longer be booked.
 *
 * <p>Two different reasons remove the same kind of row: the tutor pulled the hour back
 * ({@link HoursWithdrawn}), or a student already booked it ({@link BookingConfirmed}). Both carry
 * the affected block ids, which is all matching needs: it never re-derives why a block left
 * circulation, it just stops offering it.
 */
@Component
public class RemoveOffersOnBookingEventsListener {

    private final AvailableOfferRepository offers;

    RemoveOffersOnBookingEventsListener(AvailableOfferRepository offers) {
        this.offers = offers;
    }

    @ApplicationModuleListener
    public void on(HoursWithdrawn event) {
        offers.deleteByTenantIdAndSourceHourBlockIdIn(event.tenantId(), event.blockIds());
    }

    @ApplicationModuleListener
    public void on(BookingConfirmed event) {
        offers.deleteByTenantIdAndSourceHourBlockIdIn(event.tenantId(), event.blockIds());
    }
}
