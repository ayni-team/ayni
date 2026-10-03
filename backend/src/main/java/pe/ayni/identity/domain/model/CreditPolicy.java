package pe.ayni.identity.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;
import pe.ayni.identity.PolicyKind;

/**
 * A version of a university credit policy.
 *
 * <p>Policies are historical records. Updating a policy never overwrites the previous values:
 * the old version is superseded and a new version becomes current.
 */
@Entity
@Table(schema = "identity", name = "credit_policies")
public class CreditPolicy {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "tenant_id", length = 32, nullable = false)
    private String tenantId;

    @Enumerated(EnumType.STRING)
    @Column(name = "kind", length = 16, nullable = false)
    private PolicyKind kind;

    @Column(name = "credits_amount", nullable = false)
    private int creditsAmount;

    @Column(name = "validity_days", nullable = false)
    private int validityDays;

    @Column(name = "valid_from", nullable = false)
    private LocalDate validFrom;

    @Column(name = "superseded_at")
    private Instant supersededAt;

    @Column(name = "created_by_admin")
    private UUID createdByAdmin;

    @Column(name = "created_by_user")
    private UUID createdByUser;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected CreditPolicy() {
        // Required by JPA
    }

    public CreditPolicy(
            UUID id,
            String tenantId,
            PolicyKind kind,
            int creditsAmount,
            int validityDays,
            LocalDate validFrom,
            UUID createdByAdmin,
            UUID createdByUser,
            Instant createdAt) {

        this.id =
                Objects.requireNonNull(
                        id,
                        "id must not be null");

        this.tenantId =
                requireText(
                        tenantId,
                        "tenantId");

        this.kind =
                Objects.requireNonNull(
                        kind,
                        "kind must not be null");

        if (creditsAmount <= 0) {
            throw new IdentityRuleViolation(
                    "Credits amount must be greater than zero");
        }

        if (validityDays <= 0) {
            throw new IdentityRuleViolation(
                    "Validity days must be greater than zero");
        }

        if ((createdByAdmin == null)
                == (createdByUser == null)) {

            throw new IdentityRuleViolation(
                    "A credit policy must have exactly one author");
        }

        this.creditsAmount =
                creditsAmount;

        this.validityDays =
                validityDays;

        this.validFrom =
                Objects.requireNonNull(
                        validFrom,
                        "validFrom must not be null");

        this.createdByAdmin =
                createdByAdmin;

        this.createdByUser =
                createdByUser;

        this.createdAt =
                Objects.requireNonNull(
                        createdAt,
                        "createdAt must not be null");
    }

    /**
     * Closes this version of the policy without deleting it.
     */
    public void supersede(Instant now) {

        Objects.requireNonNull(
                now,
                "now must not be null");

        if (supersededAt != null) {
            throw new IdentityRuleViolation(
                    "The credit policy was already superseded");
        }

        supersededAt =
                now;
    }

    private static String requireText(
            String value,
            String field) {

        if (value == null
                || value.isBlank()) {

            throw new IdentityRuleViolation(
                    field + " must not be blank");
        }

        return value.trim();
    }

    public UUID getId() {
        return id;
    }

    public String getTenantId() {
        return tenantId;
    }

    public PolicyKind getKind() {
        return kind;
    }

    public int getCreditsAmount() {
        return creditsAmount;
    }

    public int getValidityDays() {
        return validityDays;
    }

    public LocalDate getValidFrom() {
        return validFrom;
    }

    public Instant getSupersededAt() {
        return supersededAt;
    }

    public UUID getCreatedByAdmin() {
        return createdByAdmin;
    }

    public UUID getCreatedByUser() {
        return createdByUser;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public boolean isCurrent() {
        return supersededAt == null;
    }
}