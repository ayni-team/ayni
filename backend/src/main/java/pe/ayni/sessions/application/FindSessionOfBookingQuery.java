package pe.ayni.sessions.application;

import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.ayni.sessions.domain.model.NotAParticipant;
import pe.ayni.sessions.domain.model.Session;
import pe.ayni.sessions.infrastructure.SessionRepository;
import pe.ayni.shared.tenancy.TenantContext;

/**
 * The session of a booking, for the student or the tutor of it and nobody else.
 *
 * <p>Booking marks its tutorings as joinable but cannot name their session, since sessions already
 * depends on booking: whoever holds the booking's identifier asks here instead. Who belongs to the
 * session is decided by the session itself, as for reading it. The room name is not part of the
 * answer: joining hands it over.
 */
@Service
public class FindSessionOfBookingQuery {

  private final SessionRepository sessions;

  FindSessionOfBookingQuery(SessionRepository sessions) {
    this.sessions = sessions;
  }

  /**
   * @throws NoSuchElementException when the booking has no session in the current university
   * @throws NotAParticipant when the reader is neither the student nor the tutor
   */
  @Transactional(readOnly = true)
  public SessionOfBooking execute(UUID bookingId, UUID userId) {
    Objects.requireNonNull(bookingId, "bookingId must not be null");
    Objects.requireNonNull(userId, "userId must not be null");

    Session session =
        sessions
            .findByTenantIdAndBookingId(TenantContext.require(), bookingId)
            .orElseThrow(
                () -> new NoSuchElementException("No session for booking: " + bookingId));
    session.roleOf(userId);

    return new SessionOfBooking(
        session.getId(),
        session.getBookingId(),
        session.getStatus(),
        session.joinOpensAt(),
        session.getScheduledEnd());
  }
}
