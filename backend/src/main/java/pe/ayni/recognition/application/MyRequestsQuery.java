package pe.ayni.recognition.application;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.ayni.identity.IdentityApi;
import pe.ayni.recognition.domain.model.RecognitionRequest;
import pe.ayni.recognition.domain.model.RequestedSession;
import pe.ayni.recognition.infrastructure.RecognitionRequestRepository;
import pe.ayni.recognition.infrastructure.RequestedSessionRepository;
import pe.ayni.shared.tenancy.TenantContext;

/**
 * US28, scenario 5: a student follows their requests: the state of each one and, once the university
 * decided, the decision and the reason.
 *
 * <p>The figures and the sessions are the ones the request was submitted with, which is what the
 * coordinator is reading. Only the student's own requests are returned: there is no way to ask for
 * another one's.
 */
@Service
public class MyRequestsQuery {

  private final IdentityApi identity;
  private final RecognitionRequestRepository requests;
  private final RequestedSessionRepository requestedSessions;

  MyRequestsQuery(
      IdentityApi identity,
      RecognitionRequestRepository requests,
      RequestedSessionRepository requestedSessions) {
    this.identity = identity;
    this.requests = requests;
    this.requestedSessions = requestedSessions;
  }

  /**
   * @return the student's requests, the latest first, each with its sessions
   * @throws java.util.NoSuchElementException when the person is not a user of this university
   */
  @Transactional(readOnly = true)
  public List<RequestFile> of(UUID studentId) {
    Objects.requireNonNull(studentId, "studentId must not be null");
    identity.requireUser(studentId);
    String tenantId = TenantContext.require();

    List<RecognitionRequest> mine =
        requests.findByTenantIdAndStudentIdOrderBySubmittedAtDesc(tenantId, studentId);
    if (mine.isEmpty()) {
      return List.of();
    }
    Map<UUID, List<RequestedSession>> sessionsByRequest = new HashMap<>();
    requestedSessions
        .findByRequestIdInOrderByStartedAtAsc(mine.stream().map(RecognitionRequest::getId).toList())
        .forEach(
            session ->
                sessionsByRequest.computeIfAbsent(session.getRequestId(), id -> new java.util.ArrayList<>()).add(session));
    return mine.stream()
        .map(request -> new RequestFile(request, sessionsByRequest.getOrDefault(request.getId(), List.of())))
        .toList();
  }
}
