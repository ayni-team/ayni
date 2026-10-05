package pe.ayni.recognition.application;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.ayni.recognition.domain.model.RecognitionRequest;
import pe.ayni.recognition.domain.model.RequestedSession;
import pe.ayni.recognition.infrastructure.RecognitionRequestRepository;
import pe.ayni.recognition.infrastructure.RequestedSessionRepository;
import pe.ayni.shared.tenancy.TenantContext;
import pe.ayni.skills.CatalogItemView;
import pe.ayni.skills.SkillsApi;

/**
 * US30, scenarios 1 and 3: the tutoring sessions that back a request, so the coordinator decides with
 * concrete evidence and not only with a total.
 *
 * <p>Each session comes with its date, its duration and the course or skill that was taught. The hours
 * listed add up to the total the student presented: both are the ones the request copied when it was
 * submitted, so the list and the total cannot disagree.
 */
@Service
public class SupportingSessionsQuery {

  private final CoordinatorGuard coordinators;
  private final RecognitionRequestRepository requests;
  private final RequestedSessionRepository requestedSessions;
  private final SkillsApi skills;

  SupportingSessionsQuery(
      CoordinatorGuard coordinators,
      RecognitionRequestRepository requests,
      RequestedSessionRepository requestedSessions,
      SkillsApi skills) {
    this.coordinators = coordinators;
    this.requests = requests;
    this.requestedSessions = requestedSessions;
    this.skills = skills;
  }

  /** A session that backs the request, with what was taught in it. */
  public record SupportingSession(RequestedSession session, CatalogItemView taught) {}

  /** The request and the sessions behind it, the oldest first. */
  public record Support(RecognitionRequest request, List<SupportingSession> sessions) {}

  /**
   * @throws pe.ayni.recognition.domain.model.NotACoordinator when the person is not a coordinator
   * @throws NoSuchElementException when the request does not exist in this university
   */
  @Transactional(readOnly = true)
  public Support of(UUID coordinatorId, UUID requestId) {
    Objects.requireNonNull(coordinatorId, "coordinatorId must not be null");
    Objects.requireNonNull(requestId, "requestId must not be null");
    coordinators.require(coordinatorId);
    String tenantId = TenantContext.require();

    RecognitionRequest request =
        requests
            .findByIdAndTenantId(requestId, tenantId)
            .orElseThrow(() -> new NoSuchElementException("recognition request %s not found".formatted(requestId)));

    Map<UUID, CatalogItemView> taught = new HashMap<>();
    List<SupportingSession> sessions =
        requestedSessions.findByRequestIdOrderByStartedAtAsc(request.getId()).stream()
            .map(
                session ->
                    new SupportingSession(
                        session, taught.computeIfAbsent(session.getCatalogItemId(), skills::requireItem)))
            .toList();
    return new Support(request, sessions);
  }
}
