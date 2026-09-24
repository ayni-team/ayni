package pe.ayni.sessions.application;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;
import pe.ayni.shared.events.BookingConfirmed;
import pe.ayni.shared.tenancy.TenantContext;

/**
 * What sessions does about things that happened elsewhere.
 *
 * <p>A confirmed booking is what creates a session, as the backend guide says: booking does not
 * know sessions exists, it announces the confirmation and sessions reacts. The listener runs after
 * booking's transaction commits, so a booking that rolled back never gets a session.
 *
 * <p>It binds the university from the event before doing anything, because it runs outside the
 * request that confirmed the booking.
 */
@Component
class SessionEventListeners {

  private static final Logger log = LoggerFactory.getLogger(SessionEventListeners.class);

  private final ScheduleSessionUseCase scheduleSession;

  SessionEventListeners(ScheduleSessionUseCase scheduleSession) {
    this.scheduleSession = scheduleSession;
  }

  @ApplicationModuleListener
  void on(BookingConfirmed event) {
    TenantContext.runAs(
        event.tenantId(),
        () -> {
          if (scheduleSession.forBooking(
              event.bookingId(),
              event.studentId(),
              event.tutorId(),
              event.startsAt(),
              event.endsAt())) {
            log.debug("Scheduled the session of booking {}", event.bookingId());
          }
        });
  }
}
