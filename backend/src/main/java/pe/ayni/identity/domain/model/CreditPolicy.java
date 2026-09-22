package pe.ayni.identity.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import pe.ayni.identity.PolicyKind;

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

    protected CreditPolicy() {
        // Required by JPA
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

    public boolean isCurrent() {
        return supersededAt == null;
    }
}