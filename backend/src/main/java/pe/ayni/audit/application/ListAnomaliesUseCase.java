package pe.ayni.audit.application;

import java.time.Instant;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.ayni.audit.domain.model.Anomaly;
import pe.ayni.audit.domain.model.AnomalySession;
import pe.ayni.audit.domain.model.AnomalyStatus;
import pe.ayni.audit.domain.model.AuditAccessDenied;
import pe.ayni.audit.domain.model.InvalidStatusFilter;
import pe.ayni.audit.infrastructure.AnomalyRepository;
import pe.ayni.audit.infrastructure.AnomalySessionRepository;
import pe.ayni.shared.tenancy.TenantContext;

/** US47: a coordinator reads the alerts of their university, filtered by status. */
@Service
public class ListAnomaliesUseCase {

  /** The most alerts a page may hold. */
  public static final int MOST_PER_PAGE = 100;

  /** One alert as a list shows it. */
  public record Item(
      UUID id,
      String kind,
      String severity,
      String status,
      UUID subjectId,
      String description,
      int sessionCount,
      Instant detectedAt) {}

  /** A page of alerts, the most recently detected first. */
  public record AnomalyPage(List<Item> items, int page, int size, long total) {}

  private final CoordinatorGuard guard;
  private final AnomalyRepository anomalies;
  private final AnomalySessionRepository supportingSessions;

  ListAnomaliesUseCase(
      CoordinatorGuard guard, AnomalyRepository anomalies, AnomalySessionRepository supportingSessions) {
    this.guard = guard;
    this.anomalies = anomalies;
    this.supportingSessions = supportingSessions;
  }

  /**
   * @param status OPEN, DISMISSED or CONFIRMED in any case, or {@code null} for every alert
   * @param page from zero; below zero it is zero
   * @param size alerts per page, at least 1 and at most {@link #MOST_PER_PAGE}
   * @throws AuditAccessDenied when they are not an active coordinator of the current university
   * @throws InvalidStatusFilter when the status is not one an alert can have
   */
  @Transactional(readOnly = true)
  public AnomalyPage execute(UUID coordinatorId, String status, int page, int size) {
    String tenantId = TenantContext.require();
    guard.require(coordinatorId);
    AnomalyStatus wanted = parse(status);
    Pageable pageable =
        PageRequest.of(
            Math.max(page, 0),
            Math.min(Math.max(size, 1), MOST_PER_PAGE),
            Sort.by(Sort.Order.desc("detectedAt"), Sort.Order.desc("id")));
    Page<Anomaly> found =
        wanted == null
            ? anomalies.findByTenantId(tenantId, pageable)
            : anomalies.findByTenantIdAndStatus(tenantId, wanted, pageable);

    Collection<UUID> ids = found.getContent().stream().map(Anomaly::getId).toList();
    Map<UUID, Integer> sessionCounts = new HashMap<>();
    if (!ids.isEmpty()) {
      supportingSessions.findByTenantIdAndAnomalyIdIn(tenantId, ids).stream()
          .collect(Collectors.groupingBy(AnomalySession::getAnomalyId, Collectors.counting()))
          .forEach((anomalyId, count) -> sessionCounts.put(anomalyId, count.intValue()));
    }
    return new AnomalyPage(
        found.getContent().stream()
            .map(
                anomaly ->
                    new Item(
                        anomaly.getId(),
                        anomaly.getKind().name(),
                        anomaly.getSeverity().name(),
                        anomaly.getStatus().name(),
                        anomaly.getSubjectId(),
                        anomaly.description(),
                        sessionCounts.getOrDefault(anomaly.getId(), 0),
                        anomaly.getDetectedAt()))
            .toList(),
        found.getNumber(),
        found.getSize(),
        found.getTotalElements());
  }

  private static AnomalyStatus parse(String status) {
    if (status == null || status.isBlank()) {
      return null;
    }
    try {
      return AnomalyStatus.valueOf(status.strip().toUpperCase(Locale.ROOT));
    } catch (IllegalArgumentException unknown) {
      throw new InvalidStatusFilter(status);
    }
  }
}
