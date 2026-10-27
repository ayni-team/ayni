package pe.ayni.identity.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(schema = "identity", name = "access_links")
public class AccessLink {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "tenant_id", length = 32)
    private String tenantId;

    @Column(name = "email", length = 160, nullable = false)
    private String email;

    @Enumerated(EnumType.STRING)
    @Column(name = "purpose", length = 24, nullable = false)
    private AccessPurpose purpose;

    @Column(name = "token_hash", length = 64, nullable = false, unique = true)
    private String tokenHash;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "consumed_at")
    private Instant consumedAt;

    @Column(name = "requested_ip", length = 45)
    private String requestedIp;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected AccessLink() {
        // Required by JPA
    }

    public AccessLink(
            UUID id,
            String tenantId,
            String email,
            AccessPurpose purpose,
            String tokenHash,
            Instant expiresAt,
            String requestedIp,
            Instant createdAt) {

        this.id = Objects.requireNonNull(id, "id must not be null");
        this.tenantId = tenantId;
        this.email = Objects.requireNonNull(email, "email must not be null");
        this.purpose = Objects.requireNonNull(purpose, "purpose must not be null");
        this.tokenHash = Objects.requireNonNull(tokenHash, "tokenHash must not be null");
        this.expiresAt = Objects.requireNonNull(expiresAt, "expiresAt must not be null");
        this.requestedIp = requestedIp;
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt must not be null");
    }

    public boolean isUsable(Instant now) {
        Objects.requireNonNull(now, "now must not be null");

        return consumedAt == null && now.isBefore(expiresAt);
    }

    public void consume(Instant now) {
        Objects.requireNonNull(now, "now must not be null");

        if (!isUsable(now)) {
            throw new IllegalStateException("Access link is expired or already consumed");
        }

        this.consumedAt = now;
    }

    public UUID getId() {
        return id;
    }

    public String getTenantId() {
        return tenantId;
    }

    public String getEmail() {
        return email;
    }

    public AccessPurpose getPurpose() {
        return purpose;
    }

    public String getTokenHash() {
        return tokenHash;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public Instant getConsumedAt() {
        return consumedAt;
    }

    public String getRequestedIp() {
        return requestedIp;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}