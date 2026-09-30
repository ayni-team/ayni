package pe.ayni.sessions.application;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.ayni.booking.BookingApi;
import pe.ayni.booking.BookingView;
import pe.ayni.booking.BookingStatus;
import pe.ayni.sessions.domain.model.PresenceCheck;
import pe.ayni.sessions.domain.model.Session;
import pe.ayni.sessions.infrastructure.PresenceCheckRepository;
import pe.ayni.sessions.infrastructure.SessionRepository;
import pe.ayni.shared.events.SessionAbandoned;
import pe.ayni.shared.tenancy.TenantContext;

/**
 * US06: abandons a session ten minutes after its scheduled start when its tutor has not checked in.
 */
@Service
public class AbandonTutorNoShowUseCase {

  private final SessionRepository sessions;
  private final PresenceCheckRepository presenceChecks;
  private final BookingApi booking;
  private final ApplicationEventPublisher events;
  private final Clock clock;

  AbandonTutorNoShowUseCase(
      SessionRepository sessions,
      PresenceCheckRepository presenceChecks,
      BookingApi booking,
      ApplicationEventPublisher events,
      Clock clock) {
    this.sessions = sessions;
    this.presenceChecks = presenceChecks;
    this.booking = booking;
    this.events = events;
    this.clock = clock;
  }

  /** The current university's sessions whose ten-minute deadline has passed. */
  @Transactional(readOnly = true)
  public List<UUID> dueNow() {
    Instant now = clock.instant();
    return sessions.findDueForTutorNoShow(
        TenantContext.require(), now.minus(Session.TUTOR_NO_SHOW_AFTER));
  }

  /**
   * Abandons one session if its tutor still has not confirmed presence.
   *
   * @return whether the session was abandoned now
   * @throws NoSuchElementException when the session does not exist in the current university
   */
  @Transactional
  public boolean abandonFor(UUID sessionId) {
    Objects.requireNonNull(sessionId, "sessionId must not be null");

    String tenantId = TenantContext.require();
    Instant now = clock.instant();
    Session session =
        sessions
            .lockByTenantIdAndId(tenantId, sessionId)
            .orElseThrow(() -> new NoSuchElementException("Session not found: " + sessionId));

    if (!session.isDueForTutorNoShow(now)
        || isConfirmed(tenantId, sessionId, session.getTutorId())) {
      return false;
    }

    boolean studentCheckedIn = isConfirmed(tenantId, sessionId, session.getStudentId());
    BookingView confirmedBooking = booking.requireBooking(session.getBookingId());
    if (confirmedBooking.status() != BookingStatus.CONFIRMED) {
      return false;
    }
    UUID catalogItemId = confirmedBooking.catalogItemId();
    session.abandon(now);
    events.publishEvent(
        new SessionAbandoned(
            tenantId,
            session.getId(),
            session.getBookingId(),
            session.getTutorId(),
            session.getStudentId(),
            catalogItemId,
            studentCheckedIn,
            now));
    return true;
  }

  private boolean isConfirmed(String tenantId, UUID sessionId, UUID userId) {
    return presenceChecks
        .findByTenantIdAndSessionIdAndUserId(tenantId, sessionId, userId)
        .map(PresenceCheck::isConfirmed)
        .orElse(false);
  }
}
