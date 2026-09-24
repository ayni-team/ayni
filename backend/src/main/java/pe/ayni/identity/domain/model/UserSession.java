package pe.ayni.identity.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(schema = "identity", name = "user_sessions")
public class UserSession {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "tenant_id", length = 32)
    private String tenantId;

    @Column(name = "user_id")
    private UUID userId;

    @Column(name = "admin_id")
    private UUID adminId;

    @Column(name = "token_hash", length = 64, nullable = false, unique = true)
    private String tokenHash;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected UserSession() {
        // Required by JPA
    }

    public UserSession(
            UUID id,
            String tenantId,
            UUID userId,
            String tokenHash,
            Instant expiresAt,
            Instant createdAt) {

        this.id = Objects.requireNonNull(id, "id must not be null");
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId must not be null");
        this.userId = Objects.requireNonNull(userId, "userId must not be null");
        this.tokenHash = Objects.requireNonNull(tokenHash, "tokenHash must not be null");
        this.expiresAt = Objects.requireNonNull(expiresAt, "expiresAt must not be null");
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt must not be null");
    }

    public boolean isActive(Instant now) {
        Objects.requireNonNull(now, "now must not be null");

        return revokedAt == null && now.isBefore(expiresAt);
    }

    public void revoke(Instant now) {
        this.revokedAt =
                Objects.requireNonNull(now, "now must not be null");
    }

    public UUID getId() {
        return id;
    }

    public String getTenantId() {
        return tenantId;
    }

    public UUID getUserId() {
        return userId;
    }

    public UUID getAdminId() {
        return adminId;
    }

    public String getTokenHash() {
        return tokenHash;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public Instant getRevokedAt() {
        return revokedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}