package pe.ayni.sessions.application;

import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.ayni.sessions.SessionStatus;
import pe.ayni.sessions.infrastructure.SessionRepository;
import pe.ayni.shared.tenancy.TenantContext;

/** Moves the scheduled session of a cancelled booking to the CANCELLED state. */
@Service
class CancelScheduledSessionUseCase {

  private final SessionRepository sessions;

  CancelScheduledSessionUseCase(SessionRepository sessions) {
    this.sessions = sessions;
  }

  @Transactional
  void execute(UUID bookingId, Instant cancelledAt) {
    String tenantId = TenantContext.require();
    sessions
        .lockByTenantIdAndBookingId(tenantId, bookingId)
        .filter(session -> session.getStatus() == SessionStatus.SCHEDULED)
        .ifPresent(session -> session.cancel(cancelledAt));
  }
}
