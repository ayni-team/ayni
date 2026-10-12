package pe.ayni.audit.application;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import pe.ayni.audit.domain.model.ActorRole;

/**
 * What a listener says happened, before it becomes an entry.
 *
 * @param actorId {@code null} when the event does not name who acted
 * @param resourceId {@code null} when the action is not about one resource
 * @param details what describes the fact; only the ones that have a value are kept
 * @param occurredAt when it happened, according to the event; {@code null} means now
 */
record NewActivity(
    UUID actorId,
    ActorRole role,
    String action,
    String resourceType,
    UUID resourceId,
    Map<String, String> details,
    Instant occurredAt) {

  /** Builds a map from name and value pairs, leaving out what has no value. */
  static Map<String, String> details(Object... namesAndValues) {
    Map<String, String> details = new LinkedHashMap<>();
    for (int i = 0; i < namesAndValues.length; i += 2) {
      Object value = namesAndValues[i + 1];
      if (value != null) {
        details.put((String) namesAndValues[i], value.toString());
      }
    }
    return details;
  }
}
