package pe.ayni.recognition.application;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * What the audit of the platform found odd about each session, where it found anything.
 *
 * <p>A port, because the anomalies belong to audit and that module does not publish them yet.
 * Recognition asks here and does not care where the answer comes from: when audit offers it, an
 * adapter replaces the one that has nothing to say, and nothing else changes.
 */
public interface SessionAlerts {

  /** What was detected about a session. */
  record Alert(String kind, String severity, String description) {}

  /**
   * @return the alerts of each session that has any; the others are not in the map
   */
  Map<UUID, List<Alert>> alertsOf(Collection<UUID> sessionIds);
}
