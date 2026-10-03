package pe.ayni.booking.application;

import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;
import pe.ayni.shared.events.SessionAbandoned;
import pe.ayni.shared.tenancy.TenantContext;

/** Applies booking lifecycle changes announced by other modules. */
@Component
class BookingEventListeners {

  private final RecordBookingNoShow recordBookingNoShow;

  BookingEventListeners(RecordBookingNoShow recordBookingNoShow) {
    this.recordBookingNoShow = recordBookingNoShow;
  }

  @ApplicationModuleListener
  void on(SessionAbandoned event) {
    TenantContext.runAs(
        event.tenantId(), () -> recordBookingNoShow.record(event.bookingId()));
  }
}
