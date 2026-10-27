package pe.ayni.audit.application;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.ayni.audit.AlertView;
import pe.ayni.audit.AuditApi;
import pe.ayni.audit.domain.model.Anomaly;
import pe.ayni.audit.domain.model.AnomalySession;
import pe.ayni.audit.domain.model.AnomalyStatus;
import pe.ayni.audit.infrastructure.AnomalyRepository;
import pe.ayni.audit.infrastructure.AnomalySessionRepository;
import pe.ayni.shared.tenancy.TenantContext;

/** What audit tells the other modules: the alerts that are still open about some sessions. */
@Service
class AuditApiImpl implements AuditApi {

  private final AnomalyRepository anomalies;
  private final AnomalySessionRepository supportingSessions;

  AuditApiImpl(AnomalyRepository anomalies, AnomalySessionRepository supportingSessions) {
    this.anomalies = anomalies;
    this.supportingSessions = supportingSessions;
  }

  @Override
  @Transactional(readOnly = true)
  public Map<UUID, List<AlertView>> openAlertsOf(Collection<UUID> sessionIds) {
    Objects.requireNonNull(sessionIds, "sessionIds must not be null");
    if (sessionIds.isEmpty()) {
      return Map.of();
    }
    String tenantId = TenantContext.require();
    List<AnomalySession> links = supportingSessions.findByTenantIdAndSessionIdIn(tenantId, sessionIds);
    if (links.isEmpty()) {
      return Map.of();
    }
    Map<UUID, Anomaly> open =
        anomalies
            .findByTenantIdAndIdInAndStatus(
                tenantId,
                links.stream().map(AnomalySession::getAnomalyId).distinct().toList(),
                AnomalyStatus.OPEN)
            .stream()
            .collect(Collectors.toMap(Anomaly::getId, Function.identity()));

    Map<UUID, List<AlertView>> alerts = new HashMap<>();
    for (AnomalySession link : links) {
      Anomaly anomaly = open.get(link.getAnomalyId());
      if (anomaly != null) {
        alerts
            .computeIfAbsent(link.getSessionId(), session -> new ArrayList<>())
            .add(
                new AlertView(
                    anomaly.getId(),
                    anomaly.getKind().name(),
                    anomaly.getSeverity().name(),
                    anomaly.description(),
                    anomaly.getDetectedAt()));
      }
    }
    alerts.values().forEach(list -> list.sort(Comparator.comparing(AlertView::detectedAt).thenComparing(AlertView::id)));
    return alerts;
  }
}
