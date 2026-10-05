package pe.ayni.recognition.application;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.ayni.booking.BookingApi;
import pe.ayni.identity.IdentityApi;
import pe.ayni.recognition.domain.model.InsufficientHours;
import pe.ayni.recognition.domain.model.RecognitionRequest;
import pe.ayni.recognition.domain.model.RecognitionRule;
import pe.ayni.recognition.domain.model.RecognitionStateConflict;
import pe.ayni.recognition.domain.model.RequestedSession;
import pe.ayni.recognition.infrastructure.RecognitionRequestRepository;
import pe.ayni.recognition.infrastructure.RecognitionRuleRepository;
import pe.ayni.recognition.infrastructure.RequestedSessionRepository;
import pe.ayni.sessions.SessionSummary;
import pe.ayni.shared.events.RecognitionRequested;
import pe.ayni.shared.tenancy.TenantContext;

/**
 * US28: a student who reached the hours their university asks for submits a request, backed by the
 * sessions they taught.
 *
 * <p>The request is registered with the total of hours, the sessions that back it and the ratings
 * the tutor received. These figures are copied now and never change, so the coordinator reads what
 * the student presented.
 *
 * <p>Which sessions back it: the oldest ones that no request has used, until the hours the
 * university asks for are reached. The student keeps the rest of their sessions for the next
 * request, and a session backs one request and never a second one. The credits are not touched:
 * nobody is punished for asking to be recognised.
 *
 * <p>Whoever submits waits for their turn per student, so two requests sent at once cannot both take
 * the same sessions: the second finds them used and is told how many hours it lacks.
 */
@Service
public class SubmitRecognitionRequestUseCase {

  private final IdentityApi identity;
  private final BookingApi booking;
  private final UnclaimedSessions unclaimed;
  private final SessionRatings ratings;
  private final RecognitionRuleRepository rules;
  private final RecognitionRequestRepository requests;
  private final RequestedSessionRepository requestedSessions;
  private final ApplicationEventPublisher events;
  private final Clock clock;

  SubmitRecognitionRequestUseCase(
      IdentityApi identity,
      BookingApi booking,
      UnclaimedSessions unclaimed,
      SessionRatings ratings,
      RecognitionRuleRepository rules,
      RecognitionRequestRepository requests,
      RequestedSessionRepository requestedSessions,
      ApplicationEventPublisher events,
      Clock clock) {
    this.identity = identity;
    this.booking = booking;
    this.unclaimed = unclaimed;
    this.ratings = ratings;
    this.rules = rules;
    this.requests = requests;
    this.requestedSessions = requestedSessions;
    this.events = events;
    this.clock = clock;
  }

  /**
   * @throws java.util.NoSuchElementException when the person is not a user of this university
   * @throws RecognitionStateConflict when the university has not opened recognition
   * @throws InsufficientHours when the student has not taught the hours asked for, saying how many
   *     are missing
   */
  @Transactional
  public SubmittedRequest execute(UUID studentId) {
    Objects.requireNonNull(studentId, "studentId must not be null");
    identity.requireUser(studentId);
    String tenantId = TenantContext.require();

    // One request at a time per student: see the lock.
    requests.lockStudent(tenantId, studentId);

    Instant now = clock.instant();
    RecognitionRule rule =
        rules
            .findInForce(tenantId, LocalDate.ofInstant(now, ZoneOffset.UTC))
            .orElseThrow(
                () ->
                    new RecognitionStateConflict(
                        "your university has not opened the recognition of hours"));

    List<SessionSummary> backing = backingSessions(unclaimed.of(studentId), rule.getMinimumHours());

    Map<UUID, Integer> stars =
        ratings.starsOf(backing.stream().map(SessionSummary::sessionId).toList());
    List<RequestedSession> sessions = new ArrayList<>();
    for (SessionSummary session : backing) {
      sessions.add(
          RequestedSession.of(
              session.sessionId(),
              tenantId,
              session.hours(),
              booking.requireBooking(session.bookingId()).catalogItemId(),
              session.startedAt(),
              session.endedAt(),
              stars.get(session.sessionId())));
    }

    RecognitionRequest request =
        requests.save(RecognitionRequest.submit(UUID.randomUUID(), tenantId, studentId, sessions, now));
    requestedSessions.saveAll(sessions);

    events.publishEvent(
        new RecognitionRequested(tenantId, request.getId(), studentId, request.getTotalHours(), now));
    return new SubmittedRequest(request, sessions);
  }

  /** The oldest sessions, as many as it takes to reach the hours asked for. */
  private static List<SessionSummary> backingSessions(List<SessionSummary> available, int requiredHours) {
    int total = available.stream().mapToInt(SessionSummary::hours).sum();
    if (total < requiredHours) {
      throw new InsufficientHours(requiredHours - total);
    }
    List<SessionSummary> backing = new ArrayList<>();
    int hours = 0;
    for (SessionSummary session : available) {
      if (hours >= requiredHours) {
        break;
      }
      backing.add(session);
      hours += session.hours();
    }
    return backing;
  }

  /** The request that was registered, with the sessions that back it. */
  public record SubmittedRequest(RecognitionRequest request, List<RequestedSession> sessions) {}
}
