package pe.ayni.wallet.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import java.util.UUID;

/**
 * The credit account of one student at one university.
 *
 * <p>It holds no balance. The balance is the sum of what is left in the student's {@link CreditLot
 * groups}, and it is computed on every read. A stored balance and a history of movements are two
 * truths that drift apart on the first failed transaction, and the one that can be recomputed is
 * the one that has to go.
 */
@Entity
@Table(
    schema = "wallet",
    name = "credit_accounts",
    uniqueConstraints =
        @UniqueConstraint(
            name = "uq_credit_accounts_user",
            columnNames = {"tenant_id", "user_id"}))
public class CreditAccount {

  @Id
  @Column(name = "id", nullable = false, updatable = false)
  private UUID id;

  @Column(name = "tenant_id", length = 32, nullable = false, updatable = false)
  private String tenantId;

  @Column(name = "user_id", nullable = false, updatable = false)
  private UUID userId;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  protected CreditAccount() {
    // Required by JPA
  }

  private CreditAccount(UUID id, String tenantId, UUID userId, Instant createdAt) {
    this.id = id;
    this.tenantId = tenantId;
    this.userId = userId;
    this.createdAt = createdAt;
  }

  /** Opens the account a student did not have yet. */
  public static CreditAccount open(String tenantId, UUID userId, Instant now) {
    return new CreditAccount(UUID.randomUUID(), tenantId, userId, now);
  }

  public UUID id() {
    return id;
  }

  public String tenantId() {
    return tenantId;
  }

  public UUID userId() {
    return userId;
  }

  public Instant createdAt() {
    return createdAt;
  }
}
