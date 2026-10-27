package pe.ayni.audit.application;

import java.nio.charset.StandardCharsets;
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
 * @param id the identifier the entry must have, or {@code null} for a new one; an entry whose event
 *     can be delivered twice derives it from the event, so the second delivery finds it written
 */
record NewActivity(
    UUID actorId,
    ActorRole role,
    String action,
    String resourceType,
    UUID resourceId,
    Map<String, String> details,
    Instant occurredAt,
    UUID id) {

  /** An entry with an identifier of its own each time it is recorded. */
  NewActivity(
      UUID actorId,
      ActorRole role,
      String action,
      String resourceType,
      UUID resourceId,
      Map<String, String> details,
      Instant occurredAt) {
    this(actorId, role, action, resourceType, resourceId, details, occurredAt, null);
  }

  /** The same identifier for the same parts, so that one fact is never written twice. */
  static UUID idOf(Object... parts) {
    StringBuilder key = new StringBuilder();
    for (Object part : parts) {
      key.append(part).append('|');
    }
    return UUID.nameUUIDFromBytes(key.toString().getBytes(StandardCharsets.UTF_8));
  }

  /**
   * The identifier of the entry this activity becomes, the same however many times its event is
   * delivered (TS04): a delivery resumed from the event publication registry finds the entry the
   * first one wrote and adds nothing. It is worked out from everything the event said, so two
   * different facts never share it. An entry that was given an identifier keeps it. Without an
   * instant from the event there is nothing stable to work it out from, and the entry gets a new
   * identifier.
   */
  UUID entryId(String tenantId) {
    if (id != null) {
      return id;
    }
    if (occurredAt == null) {
      return UUID.randomUUID();
    }
    String fact =
        String.join(
            "|",
            "audit",
            tenantId,
            action,
            resourceType,
            String.valueOf(resourceId),
            String.valueOf(actorId),
            String.valueOf(role),
            occurredAt.toString(),
            String.valueOf(details));
    return UUID.nameUUIDFromBytes(fact.getBytes(StandardCharsets.UTF_8));
  }

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
