package pe.ayni.audit.domain.model;

import jakarta.persistence.*;
import org.hibernate.annotations.Immutable;
import java.time.Instant;
import java.util.UUID;

/**
 * Entity representing an audit log entry.
 */
@Entity
@Table(name = "audit_logs")
@Immutable
public class AuditLog {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private String tenantId;

    @Column(name = "user_id", updatable = false)
    private String userId;

    @Column(name = "action", nullable = false, updatable = false)
    private String action;

    @Column(name = "entity_type", nullable = false, updatable = false)
    private String entityType;

    @Column(name = "entity_id", nullable = false, updatable = false)
    private String entityId;

    @Column(name = "details", columnDefinition = "TEXT", updatable = false)
    private String details;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    /**
     * Protected constructor required by JPA.
     */
    protected AuditLog() {}

    /**
     * Creates a new audit log record.
     *
     * @param tenantId tenant identifier
     * @param userId user identifier
     * @param action performed action
     * @param entityType target entity type
     * @param entityId target entity identifier
     * @param details additional event details
     */
    public AuditLog(String tenantId, String userId, String action, String entityType, String entityId, String details) {
        this.id = UUID.randomUUID();
        this.tenantId = tenantId;
        this.userId = userId;
        this.action = action;
        this.entityType = entityType;
        this.entityId = entityId;
        this.details = details;
        this.createdAt = Instant.now();
    }

    /** @return log unique identifier */
    public UUID getId() { return id; }

    /** @return tenant identifier */
    public String getTenantId() { return tenantId; }

    /** @return user identifier */
    public String getUserId() { return userId; }

    /** @return action name */
    public String getAction() { return action; }

    /** @return target entity type */
    public String getEntityType() { return entityType; }

    /** @return target entity identifier */
    public String getEntityId() { return entityId; }

    /** @return details payload */
    public String getDetails() { return details; }

    /** @return creation timestamp */
    public Instant getCreatedAt() { return createdAt; }
}