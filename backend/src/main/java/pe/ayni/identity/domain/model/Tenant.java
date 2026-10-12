package pe.ayni.identity.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * A university registered in Ayni.
 *
 * <p>The tenant owns its institutional email domains and academic rules. An institutional email
 * is matched against those domains before a user has an account.
 */
@Entity
@Table(schema = "identity", name = "tenants")
public class Tenant {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "code", length = 32, nullable = false, unique = true, updatable = false)
    private String code;

    @Column(name = "name", length = 160, nullable = false)
    private String name;

    @Column(name = "logo_url", length = 512)
    private String logoUrl;

    @Column(name = "primary_color", length = 16)
    private String primaryColor;

    @Column(name = "secondary_color", length = 16)
    private String secondaryColor;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "email_domains", columnDefinition = "text[]", nullable = false)
    private List<String> emailDomains;

    @Column(name = "minimum_teaching_grade", precision = 4, scale = 2, nullable = false)
    private BigDecimal minimumTeachingGrade;

    @Column(name = "timezone", length = 64, nullable = false)
    private ZoneId timezone;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 16, nullable = false)
    private TenantStatus status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Tenant() {
        // Required by JPA
    }

    public Tenant(
            UUID id,
            String code,
            String name,
            String logoUrl,
            String primaryColor,
            String secondaryColor,
            List<String> emailDomains,
            BigDecimal minimumTeachingGrade,
            ZoneId timezone,
            TenantStatus status,
            Instant now) {

        this.id = Objects.requireNonNull(id, "id must not be null");
        this.code = Objects.requireNonNull(code, "code must not be null");
        this.name = Objects.requireNonNull(name, "name must not be null");
        this.logoUrl = logoUrl;
        this.primaryColor = primaryColor;
        this.secondaryColor = secondaryColor;
        this.emailDomains = List.copyOf(Objects.requireNonNull(emailDomains, "emailDomains must not be null"));
        this.minimumTeachingGrade =
                Objects.requireNonNull(minimumTeachingGrade, "minimumTeachingGrade must not be null");
        this.timezone = Objects.requireNonNull(timezone, "timezone must not be null");
        this.status = Objects.requireNonNull(status, "status must not be null");
        this.createdAt = Objects.requireNonNull(now, "now must not be null");
        this.updatedAt = now;
    }

    /**
     * Whether this university owns the domain of the supplied institutional email.
     *
     * <p>The comparison is case insensitive. Invalid email values simply do not belong to the
     * university.
     */
    public boolean claims(String email) {
        if (email == null) {
            return false;
        }

        String normalizedEmail = email.trim().toLowerCase(Locale.ROOT);
        int separator = normalizedEmail.lastIndexOf('@');

        if (separator <= 0 || separator == normalizedEmail.length() - 1) {
            return false;
        }

        String domain = normalizedEmail.substring(separator + 1);

        return emailDomains.stream()
                .map(value -> value.toLowerCase(Locale.ROOT))
                .anyMatch(domain::equals);
    }

    public boolean isActive() {
        return status == TenantStatus.ACTIVE;
    }

    public void suspend(Instant now) {
        Objects.requireNonNull(now, "now must not be null");
        this.status = TenantStatus.SUSPENDED;
        this.updatedAt = now;
    }
    public void updateIdentity(
            String logoUrl,
            String primaryColor,
            String secondaryColor,
            Instant now) {

        this.logoUrl =
                normalizeOptional(
                        logoUrl,
                        512,
                        "Logo URL");

        this.primaryColor =
                normalizeOptional(
                        primaryColor,
                        16,
                        "Primary color");

        this.secondaryColor =
                normalizeOptional(
                        secondaryColor,
                        16,
                        "Secondary color");

        this.updatedAt =
                Objects.requireNonNull(
                        now,
                        "now must not be null");
    }

    private String normalizeOptional(
            String value,
            int maxLength,
            String field) {

        if (value == null
                || value.isBlank()) {

            return null;
        }

        String normalized =
                value.trim();

        if (normalized.length() > maxLength) {
            throw new IdentityRuleViolation(
                    field + " exceeds the maximum length");
        }

        return normalized;
    }

    public UUID getId() {
        return id;
    }

    public String getCode() {
        return code;
    }

    public String getName() {
        return name;
    }

    public String getLogoUrl() {
        return logoUrl;
    }

    public String getPrimaryColor() {
        return primaryColor;
    }

    public String getSecondaryColor() {
        return secondaryColor;
    }

    public List<String> getEmailDomains() {
        return List.copyOf(emailDomains);
    }

    public BigDecimal getMinimumTeachingGrade() {
        return minimumTeachingGrade;
    }

    public ZoneId getTimezone() {
        return timezone;
    }

    public TenantStatus getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}