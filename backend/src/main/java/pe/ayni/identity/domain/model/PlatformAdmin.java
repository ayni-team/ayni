package pe.ayni.identity.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

/**
 * The person who registers universities and reads the aggregates across them.
 *
 * <p>Kept apart from {@code identity.users} on purpose, as the data model says: an administrator
 * belongs to no university and must never read academic data, so a query over students cannot
 * reach one by forgetting a condition. They sign in by link like everybody else (TS03), and the
 * session they get carries no university.
 */
@Entity
@Table(schema = "identity", name = "platform_admins")
public class PlatformAdmin {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "email", length = 160, nullable = false, unique = true)
    private String email;

    @Column(name = "full_name", length = 160, nullable = false)
    private String fullName;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 16, nullable = false)
    private PlatformAdminStatus status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected PlatformAdmin() {
        // Required by JPA
    }

    public PlatformAdmin(
            UUID id,
            String email,
            String fullName,
            PlatformAdminStatus status,
            Instant createdAt) {

        this.id = Objects.requireNonNull(id, "id must not be null");
        this.email =
                Objects.requireNonNull(email, "email must not be null")
                        .trim()
                        .toLowerCase(Locale.ROOT);
        this.fullName = Objects.requireNonNull(fullName, "fullName must not be null");
        this.status = Objects.requireNonNull(status, "status must not be null");
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt must not be null");
    }

    /** Whether this administrator may still sign in. */
    public boolean isActive() {
        return status == PlatformAdminStatus.ACTIVE;
    }

    public UUID getId() {
        return id;
    }

    public String getEmail() {
        return email;
    }

    public String getFullName() {
        return fullName;
    }

    public PlatformAdminStatus getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
