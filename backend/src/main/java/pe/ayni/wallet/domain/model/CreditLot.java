package pe.ayni.wallet.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Comparator;
import java.util.UUID;
import pe.ayni.shared.domain.CreditType;
import pe.ayni.shared.domain.Credits;

/**
 * A group of credits that arrived together, from the same origin and with the same expiry.
 *
 * <p>Credits are held in groups rather than as a number because the group is what carries the two
 * facts that matter about a credit: where it came from and when it dies. Spending takes from the
 * group closest to expiring, and a refund puts the credits back in the group they left, with the
 * expiry they had. Neither rule can be expressed against a single balance.
 */
@Entity
@Table(schema = "wallet", name = "credit_lots")
public class CreditLot {

  /**
   * The order credits are spent in: what expires first goes first, and what never expires goes
   * last.
   *
   * <p>Ties are broken by creation, and then by identifier, so that two groups expiring at the very
   * same instant are always spent in the same order. A charge that depends on the order rows happen
   * to come back in is a charge that cannot be tested.
   */
  public static final Comparator<CreditLot> EXPIRY_FIRST =
      Comparator.comparing(
              CreditLot::expiresAt, Comparator.nullsLast(Comparator.naturalOrder()))
          .thenComparing(CreditLot::createdAt)
          .thenComparing(CreditLot::id);

  @Id
  @Column(name = "id", nullable = false, updatable = false)
  private UUID id;

  @Column(name = "tenant_id", length = 32, nullable = false, updatable = false)
  private String tenantId;

  @Column(name = "account_id", nullable = false, updatable = false)
  private UUID accountId;

  @Enumerated(EnumType.STRING)
  @Column(name = "credit_type", length = 16, nullable = false, updatable = false)
  private CreditType creditType;

  @Column(name = "original_amount", nullable = false, updatable = false)
  private int originalAmount;

  /** The only column that changes. Everything else about a group is fixed when it is created. */
  @Column(name = "remaining_amount", nullable = false)
  private int remainingAmount;

  @Column(name = "expires_at", updatable = false)
  private Instant expiresAt;

  @Enumerated(EnumType.STRING)
  @Column(name = "source_type", length = 24, nullable = false, updatable = false)
  private CreditSource sourceType;

  @Column(name = "source_id", updatable = false)
  private UUID sourceId;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  protected CreditLot() {
    // Required by JPA
  }

  private CreditLot(
      UUID id,
      String tenantId,
      UUID accountId,
      CreditType creditType,
      int amount,
      Instant expiresAt,
      CreditSource sourceType,
      UUID sourceId,
      Instant createdAt) {
    this.id = id;
    this.tenantId = tenantId;
    this.accountId = accountId;
    this.creditType = creditType;
    this.originalAmount = amount;
    this.remainingAmount = amount;
    this.expiresAt = expiresAt;
    this.sourceType = sourceType;
    this.sourceId = sourceId;
    this.createdAt = createdAt;
  }

  /**
   * Places a new group of credits in an account.
   *
   * @param expiresAt required for the types that expire, {@code null} for the ones that do not
   * @throws IllegalArgumentException when the amount is zero, or when the expiry does not match
   *     what the type allows
   */
  public static CreditLot granted(
      String tenantId,
      UUID accountId,
      Credits amount,
      CreditType creditType,
      Instant expiresAt,
      CreditSource sourceType,
      UUID sourceId,
      Instant now) {

    if (amount.isZero()) {
      throw new IllegalArgumentException("A group of credits cannot be empty");
    }
    // The same rule the database enforces with ck_credit_lots_expiry. It is checked here too so
    // that the failure is a readable message in a unit test and not a constraint violation.
    if (creditType.expires() && expiresAt == null) {
      throw new IllegalArgumentException(creditType + " credits must carry an expiry date");
    }
    if (!creditType.expires() && expiresAt != null) {
      throw new IllegalArgumentException(creditType + " credits never expire");
    }

    return new CreditLot(
        UUID.randomUUID(),
        tenantId,
        accountId,
        creditType,
        amount.amount(),
        expiresAt,
        sourceType,
        sourceId,
        now);
  }

  /**
   * Takes credits out of this group.
   *
   * @throws IllegalArgumentException when the group does not hold that many
   */
  public void consume(Credits amount) {
    this.remainingAmount = remaining().minus(amount).amount();
  }

  /**
   * Puts refunded credits back, never above what the group originally held.
   *
   * <p>A group can only ever give back what it gave, so a refund cannot be used to create credits.
   *
   * @throws IllegalArgumentException when the group did not give that many away
   */
  public void restore(Credits amount) {
    int restored = remainingAmount + amount.amount();
    if (restored > originalAmount) {
      throw new IllegalArgumentException(
          "Cannot return "
              + amount.amount()
              + " credits to a group of "
              + originalAmount
              + " that still holds "
              + remainingAmount);
    }
    this.remainingAmount = restored;
  }

  /**
   * Empties the group because it reached its expiry.
   *
   * @return the credits that were lost, for the ledger entry that records it
   */
  public Credits expire() {
    Credits lost = remaining();
    this.remainingAmount = 0;
    return lost;
  }

  /** Whether the group had already died at the given moment. */
  public boolean isExpiredAt(Instant now) {
    return expiresAt != null && !now.isBefore(expiresAt);
  }

  /** Whether the group still holds credits that can be spent right now. */
  public boolean isSpendableAt(Instant now) {
    return remainingAmount > 0 && !isExpiredAt(now);
  }

  /** Whether these credits count towards the recognition the university may grant. */
  public boolean countsTowardsRecognition() {
    return creditType.countsTowardsRecognition();
  }

  public UUID id() {
    return id;
  }

  public String tenantId() {
    return tenantId;
  }

  public UUID accountId() {
    return accountId;
  }

  public CreditType creditType() {
    return creditType;
  }

  public Credits original() {
    return Credits.of(originalAmount);
  }

  public Credits remaining() {
    return Credits.of(remainingAmount);
  }

  public Instant expiresAt() {
    return expiresAt;
  }

  public CreditSource sourceType() {
    return sourceType;
  }

  public UUID sourceId() {
    return sourceId;
  }

  public Instant createdAt() {
    return createdAt;
  }
}
