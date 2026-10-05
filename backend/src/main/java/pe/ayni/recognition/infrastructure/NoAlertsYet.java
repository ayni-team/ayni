package pe.ayni.recognition.infrastructure;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;
import pe.ayni.recognition.application.SessionAlerts;

/**
 * The alerts of the sessions, until audit can tell them.
 *
 * <p>The audit module does not detect or publish anomalies yet, so no session carries an alert into a
 * request review. This adapter says so honestly instead of inventing findings. It is the only thing
 * to replace when audit publishes them.
 */
@Component
class NoAlertsYet implements SessionAlerts {

  @Override
  public Map<UUID, List<Alert>> alertsOf(Collection<UUID> sessionIds) {
    return Map.of();
  }
}
