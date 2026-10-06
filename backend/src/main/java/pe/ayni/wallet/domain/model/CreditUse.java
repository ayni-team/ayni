package pe.ayni.wallet.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import pe.ayni.shared.domain.Credits;

@Entity
@Table(
    schema = "wallet",
    name = "credit_uses",
    uniqueConstraints =
        @UniqueConstraint(
            name = "uq_credit_uses_idempotency",
            columnNames = {"tenant_id", "student_id", "idempotency_key"}))
public class CreditUse {

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

  @Column(name = "idempotency_key", length = 64, nullable = false, updatable = false)
  private String idempotencyKey;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  protected CreditUse() {
    // Required by JPA
  }

  private CreditUse(
      UUID id,
      String tenantId,
      UUID studentId,
      CreditUseKind kind,
      UUID benefitId,
      String benefitName,
      Credits credits,
      String idempotencyKey,
      Instant now) {
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
    this.idempotencyKey = Objects.requireNonNull(idempotencyKey, "idempotencyKey must not be null");
    if (idempotencyKey.isBlank() || idempotencyKey.length() > 64) {
      throw new CreditRuleViolation("An idempotency key must contain 1 to 64 characters");
    }
    this.createdAt = Objects.requireNonNull(now, "now must not be null");
    if (kind == CreditUseKind.CAMPUS_BENEFIT_REDEMPTION
        && (benefitId == null || benefitName == null || benefitName.isBlank())) {
      throw new CreditRuleViolation("A benefit redemption must identify its campus benefit");
    }
    if (kind == CreditUseKind.INCOMING_STUDENT_DONATION
        && (benefitId != null || benefitName != null)) {
      throw new CreditRuleViolation("A donation cannot reference a campus benefit");
    }
  }

  public static CreditUse benefitRedemption(
      UUID id,
      String tenantId,
      UUID studentId,
      UUID benefitId,
      String benefitName,
      Credits credits,
      String idempotencyKey,
      Instant now) {
    return new CreditUse(
        id,
        tenantId,
        studentId,
        CreditUseKind.CAMPUS_BENEFIT_REDEMPTION,
        benefitId,
        benefitName,
        credits,
        idempotencyKey,
        now);
  }

  public static CreditUse incomingStudentDonation(
      UUID id,
      String tenantId,
      UUID studentId,
      Credits credits,
      String idempotencyKey,
      Instant now) {
    return new CreditUse(
        id,
        tenantId,
        studentId,
        CreditUseKind.INCOMING_STUDENT_DONATION,
        null,
        null,
        credits,
        idempotencyKey,
        now);
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

  public String idempotencyKey() {
    return idempotencyKey;
  }

  public Instant createdAt() {
    return createdAt;
  }
}
