package pe.ayni.recognition.infrastructure;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;
import pe.ayni.audit.AlertView;
import pe.ayni.audit.AuditApi;
import pe.ayni.recognition.application.SessionAlerts;

/**
 * The alerts of the sessions, as the audit of the platform tells them.
 *
 * <p>Only the alerts still open are asked for: one that a coordinator dismissed was looked at and
 * decided, and carrying it into another review would ask the same question twice.
 */
@Component
class AuditAlerts implements SessionAlerts {

  private final AuditApi audit;

  AuditAlerts(AuditApi audit) {
    this.audit = audit;
  }

  @Override
  public Map<UUID, List<Alert>> alertsOf(Collection<UUID> sessionIds) {
    Map<UUID, List<Alert>> alerts = new HashMap<>();
    audit
        .openAlertsOf(sessionIds)
        .forEach(
            (sessionId, found) ->
                alerts.put(
                    sessionId,
                    found.stream().map(AuditAlerts::alertOf).toList()));
    return alerts;
  }

  private static Alert alertOf(AlertView view) {
    return new Alert(view.kind(), view.severity(), view.description());
  }
}
