package pe.ayni.booking.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

/**
 * An extended calendar freeze of a tutor's availability.
 *
 * <p>Inside this window, from {@code startsOn} through {@code endsOn} and including both, block
 * generation skips the tutor entirely, which takes them out of the search without touching the
 * patterns they will come back to: vacations, exam weeks, a term abroad.
 */
@Entity
@Table(schema = "booking", name = "availability_pauses")
public class AvailabilityPause {

  @Id
  @Column(name = "id", nullable = false, updatable = false)
  private UUID id;

  @Column(name = "tenant_id", length = 32, nullable = false, updatable = false)
  private String tenantId;

  @Column(name = "tutor_id", nullable = false, updatable = false)
  private UUID tutorId;

  @Column(name = "starts_on", nullable = false)
  private LocalDate startsOn;

  @Column(name = "ends_on", nullable = false)
  private LocalDate endsOn;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  protected AvailabilityPause() {
    // Required by JPA
  }

  /**
   * Pauses a tutor between two dates, both included.
   *
   * @throws BookingRuleViolation when the pause would end before it starts
   */
  public AvailabilityPause(
      UUID id, String tenantId, UUID tutorId, LocalDate startsOn, LocalDate endsOn, Instant now) {
    this.id = Objects.requireNonNull(id, "id must not be null");
    this.tenantId = Objects.requireNonNull(tenantId, "tenantId must not be null");
    this.tutorId = Objects.requireNonNull(tutorId, "tutorId must not be null");
    this.startsOn = Objects.requireNonNull(startsOn, "startsOn must not be null");
    this.endsOn = Objects.requireNonNull(endsOn, "endsOn must not be null");
    this.createdAt = Objects.requireNonNull(now, "now must not be null");

    validateInvariants();
  }

  /** The same rule ck_availability_pauses_dates enforces in the database. */
  private void validateInvariants() {
    if (this.endsOn.isBefore(this.startsOn)) {
      throw new BookingRuleViolation("ends_on must be equal to or after starts_on");
    }
  }

  /** Whether the pause covers the given date. A one day pause covers that day. */
  public boolean includes(LocalDate date) {
    Objects.requireNonNull(date, "date must not be null");
    return !date.isBefore(startsOn) && !date.isAfter(endsOn);
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

  public LocalDate getStartsOn() {
    return startsOn;
  }

  public LocalDate getEndsOn() {
    return endsOn;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }
}
