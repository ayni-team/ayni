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
import pe.ayni.sessions.domain.model.Participation;
import pe.ayni.sessions.domain.model.Session;
import pe.ayni.sessions.infrastructure.ParticipationRepository;
import pe.ayni.sessions.infrastructure.SessionRepository;
import pe.ayni.shared.events.SessionAbandoned;
import pe.ayni.shared.tenancy.TenantContext;

/**
 * US06 and US09: abandons a session ten minutes after its scheduled start when either participant
 * has not checked in.
 *
 * <p>Who is held to account depends on who was missing. A tutor who did not come while the student
 * did is a tutor no-show: the student is refunded and told, and the incident is the tutor's. A
 * student who did not come while the tutor did loses the hour, and nothing is held against the
 * tutor; when neither came, nobody is refunded or blamed. {@link SessionAbandoned} carries only
 * whether the student was present, and that is enough for its listeners to tell the three apart.
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
   * Abandons one session if either participant had not joined before the ten-minute deadline.
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

    List<Participation> arrivals =
        participations.findByTenantIdAndSessionId(tenantId, sessionId);
    boolean studentCheckedIn = session.checkedInOnTime(session.getStudentId(), arrivals);
    boolean tutorCheckedIn = session.checkedInOnTime(session.getTutorId(), arrivals);
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
}
