package pe.ayni.booking.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Objects;
import java.util.UUID;

/**
 * A tutor's recurring weekly availability window.
 *
 * <p>Availability belongs to the tutor and never to a skill: being free on Monday at five means
 * being free, and any enabled skill can be taught in that hour. Block generation projects these
 * patterns forward over the calendar to produce {@code booking.hour_blocks}.
 */
@Entity
@Table(schema = "booking", name = "availability_patterns")
public class AvailabilityPattern {

  @Id
  @Column(name = "id", nullable = false, updatable = false)
  private UUID id;

  @Column(name = "tenant_id", length = 32, nullable = false, updatable = false)
  private String tenantId;

  @Column(name = "tutor_id", nullable = false, updatable = false)
  private UUID tutorId;

  /** Stored as the 1 to 7 of the table, exposed as a {@link DayOfWeek}. 1 is Monday in both. */
  @Column(name = "day_of_week", nullable = false)
  private short dayOfWeek;

  @Column(name = "starts_at_time", nullable = false)
  private LocalTime startsAtTime;

  @Column(name = "ends_at_time", nullable = false)
  private LocalTime endsAtTime;

  @Column(name = "valid_from", nullable = false)
  private LocalDate validFrom;

  @Column(name = "valid_until")
  private LocalDate validUntil;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  protected AvailabilityPattern() {
    // Required by JPA
  }

  /**
   * Declares a weekly window.
   *
   * @param validUntil {@code null} for a pattern with no end date
   * @throws BookingRuleViolation when the window closes before it opens, or the validity does
   */
  public AvailabilityPattern(
      UUID id,
      String tenantId,
      UUID tutorId,
      DayOfWeek dayOfWeek,
      LocalTime startsAtTime,
      LocalTime endsAtTime,
      LocalDate validFrom,
      LocalDate validUntil,
      Instant now) {
    this.id = Objects.requireNonNull(id, "id must not be null");
    this.tenantId = Objects.requireNonNull(tenantId, "tenantId must not be null");
    this.tutorId = Objects.requireNonNull(tutorId, "tutorId must not be null");
    Objects.requireNonNull(dayOfWeek, "dayOfWeek must not be null");
    this.dayOfWeek = (short) dayOfWeek.getValue();
    this.startsAtTime = Objects.requireNonNull(startsAtTime, "startsAtTime must not be null");
    this.endsAtTime = Objects.requireNonNull(endsAtTime, "endsAtTime must not be null");
    this.validFrom = Objects.requireNonNull(validFrom, "validFrom must not be null");
    this.validUntil = validUntil;
    this.createdAt = Objects.requireNonNull(now, "now must not be null");

    validateInvariants();
  }

  /** The same rules ck_availability_patterns_times enforces in the database. */
  private void validateInvariants() {
    if (!this.endsAtTime.isAfter(this.startsAtTime)) {
      throw new BookingRuleViolation("ends_at_time must be strictly after starts_at_time");
    }
    if (this.validUntil != null && this.validUntil.isBefore(this.validFrom)) {
      throw new BookingRuleViolation("valid_until must be equal to or after valid_from");
    }
  }

  /**
   * Whether two patterns of the same tutor collide: same weekday, overlapping validity, overlapping
   * hours.
   *
   * <p>This is the only place the rule is written. The repository narrows the candidates down to
   * the patterns worth comparing, and the comparison itself happens here, where it can be read and
   * tested without a database. A rule expressed twice is a rule that drifts.
   *
   * <p>Touching at the edges is not colliding: a pattern that ends at five and one that starts at
   * five describe consecutive hours, not the same hour. Dates, on the other hand, are inclusive, so
   * a validity ending on the day another begins does overlap on that day.
   */
  public boolean overlaps(AvailabilityPattern other) {
    Objects.requireNonNull(other, "other pattern must not be null");

    if (this.dayOfWeek != other.dayOfWeek) {
      return false;
    }

    boolean datesOverlap =
        (this.validUntil == null || !this.validUntil.isBefore(other.validFrom))
            && (other.validUntil == null || !other.validUntil.isBefore(this.validFrom));

    if (!datesOverlap) {
      return false;
    }

    return this.startsAtTime.isBefore(other.endsAtTime)
        && other.startsAtTime.isBefore(this.endsAtTime);
  }

  public UUID getId() {
    return id;
  }

  public String getTenantId() {
    return tenantId;
  }

  public UUID getTutorId() {
    return tutorId;
  }

  public DayOfWeek getDayOfWeek() {
    return DayOfWeek.of(this.dayOfWeek);
  }

  public LocalTime getStartsAtTime() {
    return startsAtTime;
  }

  public LocalTime getEndsAtTime() {
    return endsAtTime;
  }

  public LocalDate getValidFrom() {
    return validFrom;
  }

  public LocalDate getValidUntil() {
    return validUntil;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }
}
