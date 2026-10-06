package pe.ayni.payments.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(schema = "payments", name = "purchases")
public class Purchase {

  @Id
  @Column(name = "id", nullable = false, updatable = false)
  private UUID id;

  @Column(name = "tenant_id", length = 32, nullable = false, updatable = false)
  private String tenantId;

  @Column(name = "student_id", nullable = false, updatable = false)
  private UUID studentId;

  @Column(name = "credits", nullable = false, updatable = false)
  private int credits;

  @Column(name = "amount", precision = 10, scale = 2, nullable = false, updatable = false)
  private BigDecimal amount;

  @Column(name = "currency", length = 3, nullable = false, updatable = false)
  private String currency;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", length = 16, nullable = false)
  private PurchaseStatus status;

  @Column(name = "provider_reference", length = 128)
  private String providerReference;

  @Column(name = "idempotency_key", length = 64, nullable = false, updatable = false)
  private String idempotencyKey;

  @Column(name = "confirmed_at")
  private Instant confirmedAt;

  @Column(name = "expires_at", nullable = false, updatable = false)
  private Instant expiresAt;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  protected Purchase() {
    // Required by JPA
  }

  private Purchase(
      UUID id,
      String tenantId,
      UUID studentId,
      int credits,
      BigDecimal amount,
      String currency,
      String idempotencyKey,
      Instant expiresAt,
      Instant createdAt) {
    this.id = Objects.requireNonNull(id, "id must not be null");
    this.tenantId = Objects.requireNonNull(tenantId, "tenantId must not be null");
    this.studentId = Objects.requireNonNull(studentId, "studentId must not be null");
    if (credits < 1) {
      throw new PurchaseRuleViolation("A purchase must contain at least one credit");
    }
    this.credits = credits;
    this.amount = Objects.requireNonNull(amount, "amount must not be null");
    if (amount.signum() <= 0) {
      throw new PurchaseRuleViolation("A purchase amount must be greater than zero");
    }
    this.currency = Objects.requireNonNull(currency, "currency must not be null");
    this.idempotencyKey = Objects.requireNonNull(idempotencyKey, "idempotencyKey must not be null");
    if (idempotencyKey.isBlank() || idempotencyKey.length() > 64) {
      throw new PurchaseRuleViolation("An idempotency key must contain between 1 and 64 characters");
    }
    this.createdAt = Objects.requireNonNull(createdAt, "createdAt must not be null");
    this.expiresAt = Objects.requireNonNull(expiresAt, "expiresAt must not be null");
    if (!expiresAt.isAfter(createdAt)) {
      throw new PurchaseRuleViolation("A pending purchase must expire after it is created");
    }
    this.status = PurchaseStatus.PENDING;
  }

  public static Purchase pending(
      UUID id,
      String tenantId,
      UUID studentId,
      int credits,
      BigDecimal amount,
      String currency,
      String idempotencyKey,
      Instant expiresAt,
      Instant now) {
    return new Purchase(
        id, tenantId, studentId, credits, amount, currency, idempotencyKey, expiresAt, now);
  }

  public void confirm(String providerReference, Instant now) {
    requirePending();
    this.providerReference = requireConsistentProviderReference(providerReference);
    this.confirmedAt = Objects.requireNonNull(now, "now must not be null");
    this.status = PurchaseStatus.CONFIRMED;
  }

  public void reject(String providerReference) {
    requirePending();
    this.providerReference = requireConsistentProviderReference(providerReference);
    this.status = PurchaseStatus.FAILED;
  }

  public void awaitProviderResult(String providerReference) {
    requirePending();
    this.providerReference = requireConsistentProviderReference(providerReference);
  }

  public boolean expire(Instant now) {
    Objects.requireNonNull(now, "now must not be null");
    if (status != PurchaseStatus.PENDING || expiresAt.isAfter(now)) {
      return false;
    }
    status = PurchaseStatus.EXPIRED;
    return true;
  }

  private void requirePending() {
    if (status != PurchaseStatus.PENDING) {
      throw new PurchaseRuleViolation("Only a pending purchase can receive a payment result");
    }
  }

  private String requireProviderReference(String reference) {
    Objects.requireNonNull(reference, "providerReference must not be null");
    if (reference.isBlank() || reference.length() > 128) {
      throw new PurchaseRuleViolation("A provider reference must contain 1 to 128 characters");
    }
    return reference;
  }

  private String requireConsistentProviderReference(String reference) {
    String validated = requireProviderReference(reference);
    if (providerReference != null && !providerReference.equals(validated)) {
      throw new PurchaseRuleViolation("The provider reference does not match this purchase");
    }
    return validated;
  }

  public UUID getId() {
    return id;
  }

  public String getTenantId() {
    return tenantId;
  }

  public UUID getStudentId() {
    return studentId;
  }

  public int getCredits() {
    return credits;
  }

  public BigDecimal getAmount() {
    return amount;
  }

  public String getCurrency() {
    return currency;
  }

  public PurchaseStatus getStatus() {
    return status;
  }

  public String getProviderReference() {
    return providerReference;
  }

  public String getIdempotencyKey() {
    return idempotencyKey;
  }

  public Instant getConfirmedAt() {
    return confirmedAt;
  }

  public Instant getExpiresAt() {
    return expiresAt;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }
}
