package pe.ayni.recognition.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

/**
 * What a university asks of a student before it considers recognising the hours they taught.
 *
 * <p>A university has one rule in force at a time. A rule is never edited: a new one is added with
 * the date it starts and the previous one is {@linkplain #supersede superseded}, so that a request
 * submitted under the old one can still be explained.
 */
@Entity
@Table(schema = "recognition", name = "rules")
public class RecognitionRule {

  @Id
  @Column(name = "id", nullable = false, updatable = false)
  private UUID id;

  @Column(name = "tenant_id", length = 32, nullable = false, updatable = false)
  private String tenantId;

  @Column(name = "minimum_hours", nullable = false, updatable = false)
  private int minimumHours;

  /** {@code null} when the university sets no rating. Not enforced yet: see the migration. */
  @Column(name = "minimum_rating", precision = 3, scale = 2, updatable = false)
  private BigDecimal minimumRating;

  @Column(name = "valid_from", nullable = false, updatable = false)
  private LocalDate validFrom;

  @Column(name = "superseded_at")
  private Instant supersededAt;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  protected RecognitionRule() {
    // Required by JPA
  }

  public RecognitionRule(
      UUID id,
      String tenantId,
      int minimumHours,
      BigDecimal minimumRating,
      LocalDate validFrom,
      Instant now) {
    this.id = Objects.requireNonNull(id, "id must not be null");
    this.tenantId = Objects.requireNonNull(tenantId, "tenantId must not be null");
    if (minimumHours <= 0) {
      throw new IllegalArgumentException("minimumHours must be positive");
    }
    this.minimumHours = minimumHours;
    if (minimumRating != null
        && (minimumRating.signum() < 0 || minimumRating.compareTo(new BigDecimal("5")) > 0)) {
      throw new IllegalArgumentException("minimumRating must be between 0 and 5");
    }
    this.minimumRating = minimumRating;
    this.validFrom = Objects.requireNonNull(validFrom, "validFrom must not be null");
    this.createdAt = Objects.requireNonNull(now, "now must not be null");
  }

  /**
   * The rule a coordinator sets: the hours the university asks for, from the day it says or from
   * today, the university's day. It cannot start in the past: nothing would be in force on that day
   * that was not already.
   *
   * @param validFrom the first day it applies, or {@code null} for today
   * @param today the current day in the zone of the university
   * @throws RecognitionRuleViolation when the hours are not positive or the day is already gone
   */
  public static RecognitionRule start(
      UUID id, String tenantId, int minimumHours, LocalDate validFrom, LocalDate today, Instant now) {
    if (minimumHours < 1) {
      throw new RecognitionRuleViolation("minimumHours must be at least 1");
    }
    LocalDate from = validFrom == null ? today : validFrom;
    if (from.isBefore(today)) {
      throw new RecognitionRuleViolation("validFrom cannot be before today");
    }
    return new RecognitionRule(id, tenantId, minimumHours, null, from, now);
  }

  /**
   * Whether a rule that starts on {@code nextFrom} takes this one's place. A rule starting today or
   * earlier replaces everything open; one starting later leaves the rules that start before it
   * running until then, and replaces the ones that would start on or after it.
   */
  public boolean isReplacedBy(LocalDate nextFrom, LocalDate today) {
    return supersededAt == null && (!nextFrom.isAfter(today) || !validFrom.isBefore(nextFrom));
  }

  /** Replaces this rule by a later one, which the caller adds. */
  public void supersede(Instant now) {
    this.supersededAt = Objects.requireNonNull(now, "now must not be null");
  }

  public boolean isInForceOn(LocalDate day) {
    return supersededAt == null && !validFrom.isAfter(day);
  }

  public UUID getId() {
    return id;
  }

  public String getTenantId() {
    return tenantId;
  }

  public int getMinimumHours() {
    return minimumHours;
  }

  public BigDecimal getMinimumRating() {
    return minimumRating;
  }

  public LocalDate getValidFrom() {
    return validFrom;
  }

  public Instant getSupersededAt() {
    return supersededAt;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }
}
