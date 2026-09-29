package pe.ayni.notifications.domain.model;

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
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * One notice for one person, and whether it reached them.
 *
 * <p>It is written before the email leaves and then marked sent or failed, never both, which is what
 * {@code ck_notifications_outcome} enforces. A notice that stays pending is one whose delivery was
 * interrupted, and that has to be visible too: getting in and confirming presence depend on it.
 *
 * <p>The payload describes the notice without repeating any secret it carried. An access link is
 * delivered and forgotten, exactly as identity keeps only its hash.
 */
@Entity
@Table(schema = "notifications", name = "notifications")
public class Notification {

  /** The longest reason {@code failed_reason} holds. */
  static final int MAX_REASON_LENGTH = 500;

  @Id
  @Column(name = "id", nullable = false, updatable = false)
  private UUID id;

  @Column(name = "tenant_id", length = 32, updatable = false)
  private String tenantId;

  @Column(name = "recipient_id", updatable = false)
  private UUID recipientId;

  @Column(name = "recipient_email", length = 160, nullable = false, updatable = false)
  private String recipientEmail;

  @Enumerated(EnumType.STRING)
  @Column(name = "kind", length = 40, nullable = false, updatable = false)
  private NotificationKind kind;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "payload", nullable = false, updatable = false)
  private Map<String, String> payload;

  @Column(name = "sent_at")
  private Instant sentAt;

  @Column(name = "failed_reason", length = MAX_REASON_LENGTH)
  private String failedReason;

  @Column(name = "read_at")
  private Instant readAt;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  protected Notification() {
    // Required by JPA
  }

  /**
   * A notice about to be delivered.
   *
   * @param tenantId {@code null} for somebody who belongs to no university, a platform administrator
   * @param recipientId {@code null} when the recipient has no account yet, as with an activation link
   */
  public static Notification pending(
      UUID id,
      String tenantId,
      UUID recipientId,
      String recipientEmail,
      NotificationKind kind,
      Map<String, String> payload,
      Instant now) {
    Notification notification = new Notification();
    notification.id = Objects.requireNonNull(id, "id must not be null");
    notification.tenantId = tenantId;
    notification.recipientId = recipientId;
    notification.recipientEmail =
        Objects.requireNonNull(recipientEmail, "recipientEmail must not be null");
    notification.kind = Objects.requireNonNull(kind, "kind must not be null");
    notification.payload = Map.copyOf(Objects.requireNonNull(payload, "payload must not be null"));
    notification.createdAt = Objects.requireNonNull(now, "now must not be null");
    return notification;
  }

  /** Whether nobody has recorded yet how the delivery went. */
  public boolean isPending() {
    return sentAt == null && failedReason == null;
  }

  /**
   * Records that the email left.
   *
   * @throws IllegalStateException when the outcome was already recorded
   */
  public void markSent(Instant now) {
    Objects.requireNonNull(now, "now must not be null");
    requirePending();
    this.sentAt = now;
  }

  /**
   * Records why the email did not leave, cut to what the column holds.
   *
   * @throws IllegalStateException when the outcome was already recorded
   */
  public void markFailed(String reason) {
    requirePending();
    String why = reason == null || reason.isBlank() ? "Unknown delivery failure" : reason.strip();
    this.failedReason =
        why.length() <= MAX_REASON_LENGTH ? why : why.substring(0, MAX_REASON_LENGTH);
  }

  private void requirePending() {
    if (!isPending()) {
      throw new IllegalStateException("The outcome of notification " + id + " is already recorded");
    }
  }

  public UUID getId() {
    return id;
  }

  public String getTenantId() {
    return tenantId;
  }

  public UUID getRecipientId() {
    return recipientId;
  }

  public String getRecipientEmail() {
    return recipientEmail;
  }

  public NotificationKind getKind() {
    return kind;
  }

  public Map<String, String> getPayload() {
    return payload;
  }

  public Instant getSentAt() {
    return sentAt;
  }

  public String getFailedReason() {
    return failedReason;
  }

  public Instant getReadAt() {
    return readAt;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }
}
