package pe.ayni.wallet.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import pe.ayni.shared.domain.Credits;

@Entity
@Table(schema = "wallet", name = "credit_use_confirmations")
public class CreditUseConfirmation {

  @Id
  @Column(name = "id", nullable = false, updatable = false)
  private UUID id;

  @Column(name = "tenant_id", length = 32, nullable = false, updatable = false)
  private String tenantId;

  @Column(name = "student_id", nullable = false, updatable = false)
  private UUID studentId;

  @Enumerated(EnumType.STRING)
  @Column(name = "kind", length = 32, nullable = false, updatable = false)
  private CreditUseKind kind;

  @Column(name = "benefit_id", updatable = false)
  private UUID benefitId;

  @Column(name = "benefit_name", length = 120, updatable = false)
  private String benefitName;

  @Column(name = "credits", nullable = false, updatable = false)
  private int credits;

  @Column(name = "expires_at", nullable = false, updatable = false)
  private Instant expiresAt;

  @Column(name = "confirmed_at")
  private Instant confirmedAt;

  protected CreditUseConfirmation() {
    // Required by JPA
  }

  private CreditUseConfirmation(
      UUID id,
      String tenantId,
      UUID studentId,
      CreditUseKind kind,
      UUID benefitId,
      String benefitName,
      Credits credits,
      Instant expiresAt) {
    this.id = Objects.requireNonNull(id, "id must not be null");
    this.tenantId = Objects.requireNonNull(tenantId, "tenantId must not be null");
    this.studentId = Objects.requireNonNull(studentId, "studentId must not be null");
    this.kind = Objects.requireNonNull(kind, "kind must not be null");
    this.benefitId = benefitId;
    this.benefitName = benefitName;
    Objects.requireNonNull(credits, "credits must not be null");
    if (credits.isZero()) {
      throw new CreditRuleViolation("A credit use must be greater than zero");
    }
    this.credits = credits.amount();
    this.expiresAt = Objects.requireNonNull(expiresAt, "expiresAt must not be null");
    if (kind == CreditUseKind.CAMPUS_BENEFIT_REDEMPTION
        && (benefitId == null || benefitName == null || benefitName.isBlank())) {
      throw new CreditRuleViolation("A benefit confirmation must identify its campus benefit");
    }
    if (kind == CreditUseKind.INCOMING_STUDENT_DONATION
        && (benefitId != null || benefitName != null)) {
      throw new CreditRuleViolation("A donation confirmation cannot reference a campus benefit");
    }
  }

  public static CreditUseConfirmation create(
      UUID id,
      String tenantId,
      UUID studentId,
      CreditUseKind kind,
      UUID benefitId,
      String benefitName,
      Credits credits,
      Instant expiresAt) {
    return new CreditUseConfirmation(
        id, tenantId, studentId, kind, benefitId, benefitName, credits, expiresAt);
  }

  public void confirm(Instant now) {
    if (confirmedAt != null) {
      throw new CreditRuleViolation("This credit-use confirmation has already been used");
    }
    confirmedAt = Objects.requireNonNull(now, "now must not be null");
  }

  public boolean isExpiredAt(Instant now) {
    return !expiresAt.isAfter(now);
  }

  public UUID id() {
    return id;
  }

  public String tenantId() {
    return tenantId;
  }

  public UUID studentId() {
    return studentId;
  }

  public CreditUseKind kind() {
    return kind;
  }

  public UUID benefitId() {
    return benefitId;
  }

  public String benefitName() {
    return benefitName;
  }

  public Credits credits() {
    return Credits.of(credits);
  }

  public Instant expiresAt() {
    return expiresAt;
  }

  public Instant confirmedAt() {
    return confirmedAt;
  }
}
