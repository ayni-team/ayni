package pe.ayni.audit.application;

import static pe.ayni.audit.application.NewActivity.details;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.ayni.audit.domain.model.ActivityEntry;
import pe.ayni.audit.domain.model.ActorRole;
import pe.ayni.audit.domain.model.Anomaly;
import pe.ayni.audit.domain.model.AnomalyAlreadyResolved;
import pe.ayni.audit.domain.model.AnomalyNotFound;
import pe.ayni.audit.domain.model.AuditAccessDenied;
import pe.ayni.audit.domain.model.InvalidDismissalReason;
import pe.ayni.audit.infrastructure.AnomalyRepository;
import pe.ayni.shared.tenancy.TenantContext;

/**
 * US47: a coordinator decides an alert is not a problem.
 *
 * <p>The alert is read with a lock, so two coordinators dismissing it at once are decided one after
 * the other: the second one finds it already resolved. The decision is also one more entry of the
 * activity log, written in the same transaction, so there is never a dismissal without its entry nor
 * an entry without its dismissal. The reason is free text and stays with the alert, as the reason of
 * every other decision does.
 */
@Service
public class DismissAnomalyUseCase {

  /** The action of the entry that remembers a dismissal. */
  public static final String DISMISSED = "ANOMALY_DISMISSED";

  /** What the coordinator decided. */
  public record Dismissal(UUID id, String status, UUID resolvedBy, Instant resolvedAt, String reason) {}

  private final CoordinatorGuard guard;
  private final AnomalyRepository anomalies;
  private final ActivityLogWriter log;
  private final Clock clock;

  DismissAnomalyUseCase(
      CoordinatorGuard guard, AnomalyRepository anomalies, ActivityLogWriter log, Clock clock) {
    this.guard = guard;
    this.anomalies = anomalies;
    this.log = log;
    this.clock = clock;
  }

  /**
   * @throws AuditAccessDenied when they are not an active coordinator of the current university
   * @throws AnomalyNotFound when the anomaly does not exist in the current university
   * @throws InvalidDismissalReason when the reason is missing or too long
   * @throws AnomalyAlreadyResolved when somebody decided it before
   */
  @Transactional
  public Dismissal execute(UUID coordinatorId, UUID anomalyId, String reason) {
    String tenantId = TenantContext.require();
    guard.require(coordinatorId);
    Anomaly anomaly =
        anomalies.lockByTenantIdAndId(tenantId, anomalyId).orElseThrow(AnomalyNotFound::new);

    Instant now = clock.instant();
    anomaly.dismiss(coordinatorId, reason, now);

    log.append(
        ActivityEntry.of(
            UUID.randomUUID(),
            tenantId,
            coordinatorId,
            ActorRole.COORDINATOR,
            DISMISSED,
            "ANOMALY",
            anomaly.getId(),
            details(
                "kind", anomaly.getKind(),
                "severity", anomaly.getSeverity(),
                "subjectId", anomaly.getSubjectId()),
            now));
    return new Dismissal(
        anomaly.getId(),
        anomaly.getStatus().name(),
        anomaly.getResolvedBy(),
        anomaly.getResolvedAt(),
        anomaly.getResolutionReason());
  }
}
