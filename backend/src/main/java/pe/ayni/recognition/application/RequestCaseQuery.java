package pe.ayni.recognition.application;

import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.ayni.identity.IdentityApi;
import pe.ayni.identity.UserView;
import pe.ayni.recognition.application.SessionAlerts.Alert;
import pe.ayni.recognition.domain.model.RecognitionRequest;
import pe.ayni.recognition.domain.model.RequestedSession;
import pe.ayni.recognition.infrastructure.RecognitionRequestRepository;
import pe.ayni.recognition.infrastructure.RequestedSessionRepository;
import pe.ayni.shared.tenancy.TenantContext;

/**
 * US29, scenarios 2, 3 and 5: a coordinator opens a request and reviews the case with all its
 * evidence.
 *
 * <p>The figures and each session are the ones the request was submitted with, whatever happened to
 * the student's sessions or ratings since: what the coordinator evaluates does not move. The alerts
 * of the audit are the exception, because they are not part of what the student presented: they are
 * what the platform found about those sessions, and they appear next to the evidence whenever it is
 * read.
 *
 * <p>Every session of a request was verified, since only completed sessions back one: both
 * participants confirmed the presence code.
 */
@Service
public class RequestCaseQuery {

  private final CoordinatorGuard coordinators;
  private final IdentityApi identity;
  private final RecognitionRequestRepository requests;
  private final RequestedSessionRepository requestedSessions;
  private final SessionAlerts alerts;

  RequestCaseQuery(
      CoordinatorGuard coordinators,
      IdentityApi identity,
      RecognitionRequestRepository requests,
      RequestedSessionRepository requestedSessions,
      SessionAlerts alerts) {
    this.coordinators = coordinators;
    this.identity = identity;
    this.requests = requests;
    this.requestedSessions = requestedSessions;
    this.alerts = alerts;
  }

  /** A session of the case, with what the audit found about it. */
  public record ReviewedSession(RequestedSession session, List<Alert> alerts) {}

  /** The request, the student who sent it and the evidence. */
  public record Case(RecognitionRequest request, UserView student, List<ReviewedSession> sessions) {}

  /**
   * @throws pe.ayni.recognition.domain.model.NotACoordinator when the person is not a coordinator
   * @throws NoSuchElementException when the request does not exist in this university
   */
  @Transactional(readOnly = true)
  public Case of(UUID coordinatorId, UUID requestId) {
    Objects.requireNonNull(coordinatorId, "coordinatorId must not be null");
    Objects.requireNonNull(requestId, "requestId must not be null");
    coordinators.require(coordinatorId);
    String tenantId = TenantContext.require();

    RecognitionRequest request =
        requests
            .findByIdAndTenantId(requestId, tenantId)
            .orElseThrow(() -> new NoSuchElementException("recognition request %s not found".formatted(requestId)));
    List<RequestedSession> sessions = requestedSessions.findByRequestIdOrderByStartedAtAsc(request.getId());
    Map<UUID, List<Alert>> found = alerts.alertsOf(sessions.stream().map(RequestedSession::getSessionId).toList());

    return new Case(
        request,
        identity.requireUser(request.getStudentId()),
        sessions.stream()
            .map(session -> new ReviewedSession(session, found.getOrDefault(session.getSessionId(), List.of())))
            .toList());
  }
}
