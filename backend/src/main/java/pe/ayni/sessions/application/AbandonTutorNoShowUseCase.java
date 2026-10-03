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
import pe.ayni.booking.BookingStatus;
import pe.ayni.booking.BookingView;
import pe.ayni.sessions.domain.model.ParticipantRole;
import pe.ayni.sessions.domain.model.Session;
import pe.ayni.sessions.infrastructure.ParticipationRepository;
import pe.ayni.sessions.infrastructure.SessionRepository;
import pe.ayni.shared.events.SessionAbandoned;
import pe.ayni.shared.tenancy.TenantContext;

/**
 * US06/US09: abandons a session ten minutes after its scheduled start when either participant has
 * not checked in.
 */
@Service
public class AbandonTutorNoShowUseCase {

  private final SessionRepository sessions;
  private final ParticipationRepository participations;
  private final BookingApi booking;
  private final ApplicationEventPublisher events;
  private final Clock clock;

  AbandonTutorNoShowUseCase(
      SessionRepository sessions,
      ParticipationRepository participations,
      BookingApi booking,
      ApplicationEventPublisher events,
      Clock clock) {
    this.sessions = sessions;
    this.participations = participations;
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
   * Abandons one session if either participant had not joined by the ten-minute deadline.
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

    if (!session.isDueForTutorNoShow(now)) {
      return false;
    }

    boolean studentCheckedIn =
        checkedInByDeadline(session, session.getStudentId(), ParticipantRole.STUDENT);
    boolean tutorCheckedIn =
        checkedInByDeadline(session, session.getTutorId(), ParticipantRole.TUTOR);
    if (studentCheckedIn && tutorCheckedIn) {
      return false;
    }

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

  private boolean checkedInByDeadline(
      Session session, UUID userId, ParticipantRole expectedRole) {
    return participations
        .findByTenantIdAndSessionIdAndUserId(
            session.getTenantId(), session.getId(), userId)
        .filter(participation -> participation.getRole() == expectedRole)
        .map(participation -> participation.hasCheckedInBy(session.tutorNoShowAt()))
        .orElse(false);
  }
}
