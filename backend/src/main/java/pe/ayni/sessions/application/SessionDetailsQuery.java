package pe.ayni.sessions.application;

import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.ayni.booking.BookingApi;
import pe.ayni.booking.BookingView;
import pe.ayni.sessions.domain.model.NotAParticipant;
import pe.ayni.sessions.domain.model.ParticipantRole;
import pe.ayni.sessions.domain.model.Session;
import pe.ayni.sessions.infrastructure.SessionRepository;
import pe.ayni.shared.tenancy.TenantContext;

/**
 * A session, for one of its two participants and nobody else.
 *
 * <p>The need description stays in booking, which owns it, and is read through {@link BookingApi}
 * rather than copied into sessions: the session keeps only the booking's id, as the booking module
 * already expected.
 *
 * <p>The room name is not part of the answer. It is what lets anyone into the call, so it is only
 * handed over by joining, inside the window the room is open.
 */
@Service
public class SessionDetailsQuery {

  private final SessionRepository sessions;
  private final BookingApi booking;

  SessionDetailsQuery(SessionRepository sessions, BookingApi booking) {
    this.sessions = sessions;
    this.booking = booking;
  }

  /**
   * @throws NoSuchElementException when the session does not exist in the current university
   * @throws NotAParticipant when the reader is neither the student nor the tutor
   */
  @Transactional(readOnly = true)
  public SessionDetails execute(UUID sessionId, UUID userId) {
    Objects.requireNonNull(sessionId, "sessionId must not be null");
    Objects.requireNonNull(userId, "userId must not be null");

    Session session =
        sessions
            .findByTenantIdAndId(TenantContext.require(), sessionId)
            .orElseThrow(() -> new NoSuchElementException("Session not found: " + sessionId));
    // Refused before anything else is read, booking included.
    ParticipantRole role = session.roleOf(userId);
    BookingView booked = booking.requireBooking(session.getBookingId());

    return new SessionDetails(
        session.getId(),
        session.getBookingId(),
        session.getStudentId(),
        session.getTutorId(),
        booked.catalogItemId(),
        role,
        session.getStatus(),
        session.getScheduledStart(),
        session.getScheduledEnd(),
        session.joinOpensAt(),
        session.getStartedAt(),
        booked.needDescription());
  }
}
