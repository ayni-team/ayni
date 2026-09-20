package pe.ayni.booking.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * One hour of a tutor's time: the unit the search returns and a booking takes.
 *
 * <p>One credit equals one hour, so a block is also the unit the ledger is charged in. {@code
 * UNIQUE (tenant_id, tutor_id, starts_at)} is what stops a tutor from being in two places at once,
 * and {@code version} is the optimistic lock that decides between two students confirming the same
 * hour at the same moment: one update finds a changed version and loses.
 */
@Entity
@Table(
    schema = "booking",
    name = "hour_blocks",
    uniqueConstraints =
        @UniqueConstraint(
            name = "uq_hour_blocks_tenant_tutor_start",
            columnNames = {"tenant_id", "tutor_id", "starts_at"}))
public class HourBlock {

  /**
   * How long an hour stays out of circulation while a student fills in the confirmation.
   *
   * <p>Long enough to write what you need help with, short enough that an abandoned screen does not
   * keep an hour hidden for the afternoon.
   */
  public static final Duration DEFAULT_HOLD_DURATION = Duration.ofMinutes(15);

  @Id
  @Column(name = "id", nullable = false, updatable = false)
  private UUID id;

  @Column(name = "tenant_id", length = 32, nullable = false, updatable = false)
  private String tenantId;

  @Column(name = "tutor_id", nullable = false, updatable = false)
  private UUID tutorId;

  @Column(name = "starts_at", nullable = false, updatable = false)
  private Instant startsAt;

  @Column(name = "ends_at", nullable = false, updatable = false)
  private Instant endsAt;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", length = 16, nullable = false)
  private HourBlockStatus status;

  @Column(name = "held_by")
  private UUID heldBy;

  @Column(name = "held_until")
  private Instant heldUntil;

  @Column(name = "booking_id")
  private UUID bookingId;

  @Column(name = "pattern_id")
  private UUID patternId;

  @Version
  @Column(name = "version", nullable = false)
  private long version;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  protected HourBlock() {
    // Required by JPA
  }

  /**
   * Materialises one free hour.
   *
   * @param patternId the weekly pattern it came from, {@code null} for an hour added by hand
   * @throws BookingRuleViolation when the hour ends before it starts
   */
  public HourBlock(
      UUID id,
      String tenantId,
      UUID tutorId,
      Instant startsAt,
      Instant endsAt,
      UUID patternId,
      Instant now) {
    this.id = Objects.requireNonNull(id, "id must not be null");
    this.tenantId = Objects.requireNonNull(tenantId, "tenantId must not be null");
    this.tutorId = Objects.requireNonNull(tutorId, "tutorId must not be null");
    this.startsAt = Objects.requireNonNull(startsAt, "startsAt must not be null");
    this.endsAt = Objects.requireNonNull(endsAt, "endsAt must not be null");
    this.patternId = patternId;
    this.status = HourBlockStatus.AVAILABLE;
    this.createdAt = Objects.requireNonNull(now, "now must not be null");

    validateInvariants();
  }

  /** The same rule ck_hour_blocks_range enforces in the database. */
  private void validateInvariants() {
    if (!this.endsAt.isAfter(this.startsAt)) {
      throw new BookingRuleViolation("ends_at must be strictly after starts_at");
    }
  }

  /**
   * Takes the hour out of circulation while a student decides.
   *
   * <p>An expired hold can be taken over without waiting for the job that cleans them up, which is
   * what keeps an abandoned screen from blocking an hour until the next sweep.
   *
   * @throws BookingRuleViolation when the hour is not free and its hold has not run out
   */
  public void hold(UUID studentId, Instant now) {
    Objects.requireNonNull(studentId, "studentId must not be null");
    Objects.requireNonNull(now, "now must not be null");

    if (this.status != HourBlockStatus.AVAILABLE && !isHoldExpired(now)) {
      throw new BookingRuleViolation("Cannot hold a block that is currently " + this.status);
    }

    this.status = HourBlockStatus.HELD;
    this.heldBy = studentId;
    this.heldUntil = now.plus(DEFAULT_HOLD_DURATION);
  }

  /**
   * Returns an expired hold to circulation. This is what the scheduled sweep calls.
   *
   * <p>A block that is not held is left alone, because a sweep that read a stale list should not
   * turn somebody's confirmed hour back into a free one. A hold that has not run out yet is a
   * refusal: it still belongs to the student holding it.
   *
   * @throws BookingRuleViolation when the hold is still alive
   */
  public void releaseHold(Instant now) {
    Objects.requireNonNull(now, "now must not be null");

    if (this.status != HourBlockStatus.HELD) {
      return;
    }
    if (!isHoldExpired(now)) {
      throw new BookingRuleViolation("The hold on this block has not expired yet");
    }

    this.status = HourBlockStatus.AVAILABLE;
    this.heldBy = null;
    this.heldUntil = null;
  }

  /**
   * Confirms the hour against a booking.
   *
   * @throws BookingRuleViolation when the hour is already booked or out of circulation
   */
  public void book(UUID bookingId) {
    Objects.requireNonNull(bookingId, "bookingId must not be null");

    if (this.status != HourBlockStatus.HELD && this.status != HourBlockStatus.AVAILABLE) {
      throw new BookingRuleViolation("A block cannot be booked from status " + this.status);
    }

    this.status = HourBlockStatus.BOOKED;
    this.bookingId = bookingId;
    this.heldBy = null;
    this.heldUntil = null;
  }

  /**
   * Takes a booked hour out of circulation after the booking over it was cancelled.
   *
   * @throws BookingRuleViolation when the hour was not booked
   */
  public void release() {
    if (this.status != HourBlockStatus.BOOKED) {
      throw new BookingRuleViolation("Only booked blocks can be released");
    }

    this.status = HourBlockStatus.RELEASED;
    this.bookingId = null;
    this.heldBy = null;
    this.heldUntil = null;
  }

  /** Whether this block is held by somebody whose time has run out. */
  public boolean isHoldExpired(Instant now) {
    return this.status == HourBlockStatus.HELD
        && this.heldUntil != null
        && now.isAfter(this.heldUntil);
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

  public Instant getStartsAt() {
    return startsAt;
  }

  public Instant getEndsAt() {
    return endsAt;
  }

  public HourBlockStatus getStatus() {
    return status;
  }

  public UUID getHeldBy() {
    return heldBy;
  }

  public Instant getHeldUntil() {
    return heldUntil;
  }

  public UUID getBookingId() {
    return bookingId;
  }

  public UUID getPatternId() {
    return patternId;
  }

  public long getVersion() {
    return version;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }
}
