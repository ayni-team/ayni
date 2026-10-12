package pe.ayni.recognition.application;

import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.ayni.identity.IdentityApi;
import pe.ayni.identity.UserView;
import pe.ayni.recognition.domain.model.RecognitionRequest;
import pe.ayni.recognition.domain.model.RequestedSession;
import pe.ayni.recognition.infrastructure.RecognitionRequestRepository;
import pe.ayni.recognition.infrastructure.RequestedSessionRepository;
import pe.ayni.sessions.SessionStatus;
import pe.ayni.sessions.SessionView;
import pe.ayni.sessions.SessionsApi;
import pe.ayni.shared.tenancy.TenantContext;
import pe.ayni.skills.CatalogItemView;
import pe.ayni.skills.SkillsApi;

/**
 * US30, scenario 2: a coordinator opens one of the sessions that back a request and reads its evidence:
 * who attended, whether the presence of each was verified and the rating the tutor received.
 *
 * <p>The period, the hours and the rating are the ones the request copied when it was submitted. Who
 * took part and whether they were verified are read from sessions: they belong to the session itself and
 * do not change. A session that is not part of the request is not found through it, so the coordinator
 * cannot use a request to read any session of the university.
 *
 * <p>Presence is verified when the session is {@code COMPLETED}, which is what the platform requires of
 * both participants: each one confirms the code sent to their email. Only completed sessions back a
 * request, so in practice both are verified.
 */
@Service
public class SessionEvidenceQuery {

  private final CoordinatorGuard coordinators;
  private final IdentityApi identity;
  private final SessionsApi sessions;
  private final SkillsApi skills;
  private final RecognitionRequestRepository requests;
  private final RequestedSessionRepository requestedSessions;

  SessionEvidenceQuery(
      CoordinatorGuard coordinators,
      IdentityApi identity,
      SessionsApi sessions,
      SkillsApi skills,
      RecognitionRequestRepository requests,
      RequestedSessionRepository requestedSessions) {
    this.coordinators = coordinators;
    this.identity = identity;
    this.sessions = sessions;
    this.skills = skills;
    this.requests = requests;
    this.requestedSessions = requestedSessions;
  }

  /** One participant of the session. */
  public record Participant(UserView user, boolean presenceVerified) {}

  /** The evidence of one session of a request. */
  public record Evidence(
      RecognitionRequest request,
      RequestedSession session,
      CatalogItemView taught,
      SessionView view,
      Participant tutor,
      Participant student) {}

  /**
   * @throws pe.ayni.recognition.domain.model.NotACoordinator when the person is not a coordinator
   * @throws NoSuchElementException when the request does not exist in this university, or the session
   *     is not one of those that back it
   */
  @Transactional(readOnly = true)
  public Evidence of(UUID coordinatorId, UUID requestId, UUID sessionId) {
    Objects.requireNonNull(coordinatorId, "coordinatorId must not be null");
    Objects.requireNonNull(requestId, "requestId must not be null");
    Objects.requireNonNull(sessionId, "sessionId must not be null");
    coordinators.require(coordinatorId);
    String tenantId = TenantContext.require();

    RecognitionRequest request =
        requests
            .findByIdAndTenantId(requestId, tenantId)
            .orElseThrow(() -> new NoSuchElementException("recognition request %s not found".formatted(requestId)));
    RequestedSession session =
        requestedSessions
            .findById(sessionId)
            .filter(found -> found.getRequestId().equals(request.getId()))
            .orElseThrow(
                () ->
                    new NoSuchElementException(
                        "session %s does not back recognition request %s".formatted(sessionId, requestId)));

    SessionView view = sessions.requireSession(sessionId);
    boolean verified = view.status() == SessionStatus.COMPLETED;
    return new Evidence(
        request,
        session,
        skills.requireItem(session.getCatalogItemId()),
        view,
        new Participant(identity.requireUser(view.tutorId()), verified),
        new Participant(identity.requireUser(view.studentId()), verified));
  }
}
