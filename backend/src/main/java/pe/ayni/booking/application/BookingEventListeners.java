package pe.ayni.booking.application;

import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;
import pe.ayni.shared.events.SessionAbandoned;
import pe.ayni.shared.tenancy.TenantContext;

/** Applies booking lifecycle changes announced by other modules. */
@Component
class BookingEventListeners {

  private final RecordBookingNoShow recordBookingNoShow;
  private final RecordStudentAbsence recordStudentAbsence;

  BookingEventListeners(
      RecordBookingNoShow recordBookingNoShow, RecordStudentAbsence recordStudentAbsence) {
    this.recordBookingNoShow = recordBookingNoShow;
    this.recordStudentAbsence = recordStudentAbsence;
  }

  @ApplicationModuleListener
  void on(SessionAbandoned event) {
    TenantContext.runAs(event.tenantId(), () -> recordBookingNoShow.record(event));
  }

  /**
   * US34: the same event, heard on its own so that a failure marking the booking never costs the
   * absence, nor the other way round. Only a session the student did not come to records anything.
   */
  @ApplicationModuleListener
  void onStudentAbsence(SessionAbandoned event) {
    TenantContext.runAs(event.tenantId(), () -> recordStudentAbsence.record(event));
  }
}
