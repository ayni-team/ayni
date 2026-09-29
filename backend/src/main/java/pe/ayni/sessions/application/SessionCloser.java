package pe.ayni.sessions.application;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import pe.ayni.booking.BookingApi;
import pe.ayni.sessions.domain.model.PresenceCheck;
import pe.ayni.sessions.domain.model.Session;
import pe.ayni.sessions.infrastructure.PresenceCheckRepository;
import pe.ayni.shared.domain.Credits;
import pe.ayni.shared.events.SessionCompleted;
import pe.ayni.shared.events.SessionUnverified;

/**
 * Closes a session and announces how it ended. Shared by the participants closing it and by the
 * job that closes what they left open, so both decide the outcome the same way.
 *
 * <p>The outcome is the presence check's, not the participants': the session is {@code COMPLETED}
 * only when both proved their presence with the emailed code, and {@code UNVERIFIED} otherwise,
 * including when no code was ever sent because the session ended before its fifth minute. That is
 * what makes the tutor's earned credits mean something.
 *
 * <p>{@link SessionCompleted} pays the tutor the booked hours; wallet and reputation already listen
 * to it. {@link SessionUnverified} names who did not confirm; wallet refunds the student on it.
 * Both are published inside the caller's transaction and reach their listeners once it commits.
 *
 * <p>Runs inside the caller's transaction, with the session already locked by the caller.
 */
@Component
class SessionCloser {

  private final PresenceCheckRepository presenceChecks;
  private final BookingApi booking;
  private final ApplicationEventPublisher events;

  SessionCloser(
      PresenceCheckRepository presenceChecks, BookingApi booking, ApplicationEventPublisher events) {
    this.presenceChecks = presenceChecks;
    this.booking = booking;
    this.events = events;
  }

  void close(Session session, Instant now) {
    Objects.requireNonNull(session, "session must not be null");
    Objects.requireNonNull(now, "now must not be null");

    String tenantId = session.getTenantId();
    List<UUID> unconfirmed =
        List.of(session.getStudentId(), session.getTutorId()).stream()
            .filter(
                participant ->
                    !presenceChecks
                        .findByTenantIdAndSessionIdAndUserId(tenantId, session.getId(), participant)
                        .map(PresenceCheck::isConfirmed)
                        .orElse(false))
            .toList();

    session.close(unconfirmed.isEmpty(), now);

    if (unconfirmed.isEmpty()) {
      events.publishEvent(
          new SessionCompleted(
              tenantId,
              session.getId(),
              session.getBookingId(),
              session.getTutorId(),
              session.getStudentId(),
              booking.requireBooking(session.getBookingId()).catalogItemId(),
              Credits.of(session.bookedHours()),
              now));
    } else {
      events.publishEvent(
          new SessionUnverified(
              tenantId,
              session.getId(),
              session.getBookingId(),
              session.getTutorId(),
              session.getStudentId(),
              unconfirmed,
              now));
    }
  }
}
