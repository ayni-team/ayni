package pe.ayni.audit.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * One thing that happened, written once and never changed: who acted and in what role, what they
 * did, to which resource and when.
 *
 * <p>Nothing here can be edited. The entity has no setter and Hibernate treats it as immutable, and
 * behind both the table refuses {@code UPDATE} and {@code DELETE} with a trigger, so what a
 * coordinator reads is what was written. A correction is another entry.
 *
 * <p>The metadata is a flat map of strings that describe the fact. It never carries an email
 * address, a link or a code: whoever builds an entry decides what goes in it, and this class refuses
 * a detail that looks like an address or a link.
 */
@Entity
@Immutable
@Table(schema = "audit", name = "activity_log")
public class ActivityEntry {

  static final int MAX_ACTION_LENGTH = 64;
  static final int MAX_RESOURCE_TYPE_LENGTH = 32;

  @Id
  @Column(name = "id", nullable = false, updatable = false)
  private UUID id;

  @Column(name = "tenant_id", length = 32, nullable = false, updatable = false)
  private String tenantId;

  @Column(name = "actor_id", updatable = false)
  private UUID actorId;

  @Enumerated(EnumType.STRING)
  @Column(name = "actor_role", length = 16, nullable = false, updatable = false)
  private ActorRole actorRole;

  @Column(name = "action", length = MAX_ACTION_LENGTH, nullable = false, updatable = false)
  private String action;

  @Column(name = "resource_type", length = MAX_RESOURCE_TYPE_LENGTH, nullable = false, updatable = false)
  private String resourceType;

  @Column(name = "resource_id", updatable = false)
  private UUID resourceId;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "metadata", updatable = false)
  private Map<String, String> metadata;

  @Column(name = "occurred_at", nullable = false, updatable = false)
  private Instant occurredAt;

  protected ActivityEntry() {
    // Required by JPA
  }

  /**
   * A new entry.
   *
   * @param actorId {@code null} when the event does not say who acted
   * @param resourceId {@code null} when the action is not about one particular resource
   * @param metadata what describes the fact, without personal data; may be {@code null}
   * @throws IllegalArgumentException when the action or the resource type is blank or too long
   */
  public static ActivityEntry of(
      UUID id,
      String tenantId,
      UUID actorId,
      ActorRole actorRole,
      String action,
      String resourceType,
      UUID resourceId,
      Map<String, String> metadata,
      Instant occurredAt) {
    ActivityEntry entry = new ActivityEntry();
    entry.id = Objects.requireNonNull(id, "id must not be null");
    if (tenantId == null || tenantId.isBlank()) {
      throw new IllegalArgumentException("An entry belongs to a university");
    }
    entry.tenantId = tenantId;
    entry.actorId = actorId;
    entry.actorRole = Objects.requireNonNull(actorRole, "actorRole must not be null");
    entry.action = bounded(action, MAX_ACTION_LENGTH, "action");
    entry.resourceType = bounded(resourceType, MAX_RESOURCE_TYPE_LENGTH, "resourceType");
    entry.resourceId = resourceId;
    entry.metadata = metadata == null || metadata.isEmpty() ? null : withoutPersonalData(metadata);
    entry.occurredAt = Objects.requireNonNull(occurredAt, "occurredAt must not be null");
    return entry;
  }

  private static Map<String, String> withoutPersonalData(Map<String, String> metadata) {
    for (Map.Entry<String, String> detail : metadata.entrySet()) {
      String value = detail.getValue();
      if (value != null && (value.contains("@") || value.contains("://"))) {
        throw new IllegalArgumentException(
            "The detail " + detail.getKey() + " looks like an address or a link, which an entry never keeps");
      }
    }
    return Map.copyOf(metadata);
  }

  private static String bounded(String value, int max, String name) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException("The " + name + " of an entry is required");
    }
    if (value.length() > max) {
      throw new IllegalArgumentException("The " + name + " of an entry has at most " + max + " characters");
    }
    return value;
  }

  public UUID getId() {
    return id;
  }

  public String getTenantId() {
    return tenantId;
  }

  public UUID getActorId() {
    return actorId;
  }

  public ActorRole getActorRole() {
    return actorRole;
  }

  public String getAction() {
    return action;
  }

  public String getResourceType() {
    return resourceType;
  }

  public UUID getResourceId() {
    return resourceId;
  }

  public Map<String, String> getMetadata() {
    return metadata == null ? Map.of() : metadata;
  }

  public Instant getOccurredAt() {
    return occurredAt;
  }
}
