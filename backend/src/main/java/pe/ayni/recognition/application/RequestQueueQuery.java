package pe.ayni.recognition.application;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.ayni.identity.IdentityApi;
import pe.ayni.identity.UserView;
import pe.ayni.recognition.domain.model.RecognitionRequest;
import pe.ayni.recognition.domain.model.RequestStatus;
import pe.ayni.recognition.infrastructure.RecognitionRequestRepository;
import pe.ayni.shared.tenancy.TenantContext;

/**
 * US29, scenario 1: the recognition requests waiting in the university, with who asked, the hours they
 * present and since when they wait.
 *
 * <p>The oldest first, which is the one that has waited longest. By default it is what nobody decided
 * yet; asking for a state shows the history of the decisions instead. The hours are the ones the
 * request was submitted with, not a figure that moves.
 */
@Service
public class RequestQueueQuery {

  /** What waits for a decision. */
  public static final Set<RequestStatus> PENDING = EnumSet.of(RequestStatus.SUBMITTED, RequestStatus.UNDER_REVIEW);

  public static final int MOST_PER_PAGE = 100;

  private final CoordinatorGuard coordinators;
  private final IdentityApi identity;
  private final RecognitionRequestRepository requests;

  RequestQueueQuery(
      CoordinatorGuard coordinators, IdentityApi identity, RecognitionRequestRepository requests) {
    this.coordinators = coordinators;
    this.identity = identity;
    this.requests = requests;
  }

  /** A request in the queue, with the student who sent it. */
  public record QueuedRequest(RecognitionRequest request, UserView student) {}

  public record QueuePage(List<QueuedRequest> items, int page, int size, long total) {}

  /**
   * @param status only the requests in this state, or {@code null} for the ones that wait
   * @param page from zero
   * @param size at most {@link #MOST_PER_PAGE}
   * @throws pe.ayni.recognition.domain.model.NotACoordinator when the person is not a coordinator
   */
  @Transactional(readOnly = true)
  public QueuePage of(UUID coordinatorId, RequestStatus status, int page, int size) {
    Objects.requireNonNull(coordinatorId, "coordinatorId must not be null");
    coordinators.require(coordinatorId);
    String tenantId = TenantContext.require();

    Set<RequestStatus> states = status == null ? PENDING : EnumSet.of(status);
    Page<RecognitionRequest> found =
        requests.findByTenantIdAndStatusInOrderBySubmittedAtAsc(
            tenantId, states, PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), MOST_PER_PAGE)));

    Map<UUID, UserView> students = new HashMap<>();
    List<QueuedRequest> items =
        found.getContent().stream()
            .map(
                request ->
                    new QueuedRequest(
                        request,
                        students.computeIfAbsent(request.getStudentId(), identity::requireUser)))
            .toList();
    return new QueuePage(items, found.getNumber(), found.getSize(), found.getTotalElements());
  }
}
