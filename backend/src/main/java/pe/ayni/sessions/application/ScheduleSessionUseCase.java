package pe.ayni.sessions.application;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.ayni.sessions.domain.model.Session;
import pe.ayni.sessions.infrastructure.SessionRepository;
import pe.ayni.shared.tenancy.TenantContext;

/**
 * Creates the session of a booking that was just confirmed.
 *
 * <p>Idempotent, because it runs on an event and an event can arrive twice: a booking that already
 * has its session is left as it is, which is also what {@code UNIQUE (booking_id)} would insist on.
 */
@Service
public class ScheduleSessionUseCase {

  private final SessionRepository sessions;
  private final Clock clock;

  ScheduleSessionUseCase(SessionRepository sessions, Clock clock) {
    this.sessions = sessions;
    this.clock = clock;
  }

  /** @return {@code true} when the session was created now, {@code false} when it existed */
  @Transactional
  public boolean forBooking(
      UUID bookingId, UUID studentId, UUID tutorId, Instant startsAt, Instant endsAt) {

    String tenantId = TenantContext.require();

    if (sessions.existsByTenantIdAndBookingId(tenantId, bookingId)) {
      return false;
    }
    sessions.save(
        Session.schedule(
            UUID.randomUUID(),
            tenantId,
            bookingId,
            studentId,
            tutorId,
            startsAt,
            endsAt,
            clock.instant()));
    return true;
  }
}
