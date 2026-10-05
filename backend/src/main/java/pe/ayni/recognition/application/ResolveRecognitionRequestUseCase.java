package pe.ayni.recognition.application;

import java.time.Clock;
import java.time.Instant;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.ayni.recognition.domain.model.RecognitionRequest;
import pe.ayni.recognition.infrastructure.RecognitionRequestRepository;
import pe.ayni.shared.events.RecognitionResolved;
import pe.ayni.shared.tenancy.TenantContext;

/**
 * US29, scenario 4: a coordinator approves or rejects a request, always saying why.
 *
 * <p>The decision is recorded with who took it, when and the reason, and the student reads it in
 * their requests. {@code RecognitionResolved} is published so that the student is told: this module
 * does not send notifications. Ayni certifies nothing by itself; it keeps what the university decided.
 *
 * <p>A request is decided once. The request is locked while it is decided, so two coordinators
 * deciding at once do not both succeed: the second one finds it decided and is refused.
 */
@Service
public class ResolveRecognitionRequestUseCase {

  /** What the coordinator decides. */
  public enum Decision {
    APPROVE,
    REJECT
  }

  private final CoordinatorGuard coordinators;
  private final RecognitionRequestRepository requests;
  private final ApplicationEventPublisher events;
  private final Clock clock;

  ResolveRecognitionRequestUseCase(
      CoordinatorGuard coordinators,
      RecognitionRequestRepository requests,
      ApplicationEventPublisher events,
      Clock clock) {
    this.coordinators = coordinators;
    this.requests = requests;
    this.events = events;
    this.clock = clock;
  }

  /**
   * @param reason why, as the student will read it; required for both decisions
   * @throws pe.ayni.recognition.domain.model.NotACoordinator when the person is not a coordinator
   * @throws NoSuchElementException when the request does not exist in this university
   * @throws pe.ayni.recognition.domain.model.RecognitionRuleViolation when the reason is blank or too
   *     long
   * @throws pe.ayni.recognition.domain.model.RecognitionStateConflict when it was already decided
   */
  @Transactional
  public RecognitionRequest execute(UUID coordinatorId, UUID requestId, Decision decision, String reason) {
    Objects.requireNonNull(coordinatorId, "coordinatorId must not be null");
    Objects.requireNonNull(requestId, "requestId must not be null");
    Objects.requireNonNull(decision, "decision must not be null");

    coordinators.require(coordinatorId);
    String tenantId = TenantContext.require();

    RecognitionRequest request =
        requests
            .lockByIdAndTenantId(requestId, tenantId)
            .orElseThrow(() -> new NoSuchElementException("recognition request %s not found".formatted(requestId)));

    Instant now = clock.instant();
    if (decision == Decision.APPROVE) {
      request.approve(coordinatorId, reason, now);
    } else {
      request.reject(coordinatorId, reason, now);
    }

    events.publishEvent(
        new RecognitionResolved(
            tenantId,
            request.getId(),
            request.getStudentId(),
            decision == Decision.APPROVE,
            request.getDecisionReason(),
            now));
    return request;
  }
}
