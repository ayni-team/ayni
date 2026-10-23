package pe.ayni.sessions.application;

import java.time.Duration;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.ayni.booking.BookingApi;
import pe.ayni.booking.BookingView;
import pe.ayni.sessions.SessionStatus;
import pe.ayni.sessions.domain.model.BoardContent;
import pe.ayni.sessions.domain.model.ParticipantRole;
import pe.ayni.sessions.domain.model.Session;
import pe.ayni.sessions.infrastructure.ParticipationRepository;
import pe.ayni.sessions.infrastructure.SessionRepository;
import pe.ayni.sessions.infrastructure.SupportMaterialRepository;
import pe.ayni.sessions.infrastructure.WhiteboardRepository;
import pe.ayni.shared.tenancy.TenantContext;

/**
 * US12: a participant opens a session of their history and sees what is left of it.
 *
 * <p>The whiteboard as it was left, the support material, the need and how long it really lasted.
 * A session that did not take place says what happened to it, cancelled, abandoned and who did not
 * come, or still to come, instead of showing an empty workspace; its board is absent, because it
 * never had one. Only the two participants may open it: anybody else is refused before anything
 * else is read, and another university's session is not found.
 */
@Service
public class ReviewSessionQuery {

  private final SessionRepository sessions;
  private final WhiteboardRepository whiteboards;
  private final SupportMaterialRepository materials;
  private final ParticipationRepository participations;
  private final BookingApi booking;
  private final SessionParties parties;

  ReviewSessionQuery(
      SessionRepository sessions,
      WhiteboardRepository whiteboards,
      SupportMaterialRepository materials,
      ParticipationRepository participations,
      BookingApi booking,
      SessionParties parties) {
    this.sessions = sessions;
    this.whiteboards = whiteboards;
    this.materials = materials;
    this.participations = participations;
    this.booking = booking;
    this.parties = parties;
  }

  /**
   * @throws NoSuchElementException when the session does not exist in the current university
   * @throws pe.ayni.sessions.domain.model.NotAParticipant when the reader did not take part
   */
  @Transactional(readOnly = true)
  public SessionReview execute(UUID sessionId, UUID userId) {
    Objects.requireNonNull(sessionId, "sessionId must not be null");
    Objects.requireNonNull(userId, "userId must not be null");

    String tenantId = TenantContext.require();
    Session session =
        sessions
            .findByTenantIdAndId(tenantId, sessionId)
            .orElseThrow(() -> new NoSuchElementException("Session not found: " + sessionId));
    ParticipantRole role = session.roleOf(userId);
    BookingView booked = booking.requireBooking(session.getBookingId());

    WhiteboardView board =
        session.getStartedAt() == null
            ? null
            : whiteboards
                .findByTenantIdAndSessionId(tenantId, sessionId)
                .map(
                    stored ->
                        new WhiteboardView(
                            sessionId,
                            stored.content().version(),
                            stored.content().elements(),
                            stored.hasActivity(),
                            session.getStatus() == SessionStatus.IN_PROGRESS,
                            stored.getUpdatedAt()))
                .orElseGet(
                    () ->
                        new WhiteboardView(
                            sessionId,
                            BoardContent.EMPTY.version(),
                            List.of(),
                            false,
                            session.getStatus() == SessionStatus.IN_PROGRESS,
                            null));
    SupportMaterialView material =
        materials
            .findByTenantIdAndSessionId(tenantId, sessionId)
            .map(
                found ->
                    new SupportMaterialView(
                        found.getSummary(), found.getSuggestions(), found.getGeneratedAt()))
            .orElse(null);
    List<UUID> absent =
        session.getStatus() == SessionStatus.ABANDONED
            ? session.absentAtDeadline(participations.findByTenantIdAndSessionId(tenantId, sessionId))
            : List.of();
    Duration real = session.realDuration();

    return new SessionReview(
        sessionId,
        parties.courseOf(booked.catalogItemId()),
        parties.counterpartOf(session, role),
        session.getStatus(),
        noteOf(session.getStatus()),
        session.getScheduledStart(),
        session.getScheduledEnd(),
        session.getStartedAt(),
        session.getEndedAt(),
        Duration.between(session.getScheduledStart(), session.getScheduledEnd()).toMinutes(),
        real == null ? null : real.toMinutes(),
        booked.needDescription(),
        material,
        board,
        absent);
  }

  private static String noteOf(SessionStatus status) {
    return switch (status) {
      case SCHEDULED -> "The session has not started yet";
      case IN_PROGRESS -> "The session is in progress";
      case COMPLETED -> "The session took place and both participants confirmed their presence";
      case UNVERIFIED ->
          "The session took place, but not every participant confirmed their presence";
      case ABANDONED -> "The session did not start: a participant had not come ten minutes in";
      case CANCELLED -> "The booking was cancelled before the session started";
    };
  }
}
