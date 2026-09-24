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
   * <p>Five minutes, as US03 and the hour block state diagram say: long enough to write what you
   * need help with, short enough that an abandoned screen gives the hour back quickly.
   */
  public static final Duration HOLD_DURATION = Duration.ofMinutes(5);

  private static final String STARTED = "This hour has already started and can no longer be booked";
  private static final String HELD_BY_ANOTHER =
      "Another student is holding this hour. Choose another one or try again in a few minutes";
  private static final String ALREADY_BOOKED = "This hour is already booked";
  private static final String NO_LONGER_OFFERED = "This hour is no longer offered";
  private static final String NOT_HELD =
      "You are no longer holding this hour: a hold lasts five minutes. Choose it again";

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
   * <p>Holding an hour the student already holds changes nothing, and in particular does not extend
   * it: otherwise a hold could be kept alive for as long as somebody kept asking.
   *
   * @throws HourUnavailable when the hour has started, is booked or withdrawn, or somebody else's
   *     hold on it has not run out
   */
  public void hold(UUID studentId, Instant now) {
    Objects.requireNonNull(studentId, "studentId must not be null");
    Objects.requireNonNull(now, "now must not be null");

    if (hasStartedAt(now)) {
      throw new HourUnavailable(STARTED);
    }
    if (isHeldBy(studentId, now)) {
      return;
    }
    switch (this.status) {
      case BOOKED -> throw new HourUnavailable(ALREADY_BOOKED);
      case RELEASED -> throw new HourUnavailable(NO_LONGER_OFFERED);
      case HELD -> {
        if (!isHoldExpired(now)) {
          throw new HourUnavailable(HELD_BY_ANOTHER);
        }
      }
      case AVAILABLE -> {
        // Free: nothing stands in the way.
      }
    }

    this.status = HourBlockStatus.HELD;
    this.heldBy = studentId;
    this.heldUntil = now.plus(HOLD_DURATION);
  }

  /**
   * Gives the hour back when the student holding it leaves the confirmation, or when their
   * confirmation failed.
   *
   * <p>Only the student's own hold, alive or not. Somebody else's hold, a booked hour and a free one
   * are left as they are, which makes asking twice harmless.
   *
   * @return whether the hour went back to circulation
   */
  public boolean releaseHoldOf(UUID studentId) {
    Objects.requireNonNull(studentId, "studentId must not be null");

    if (this.status != HourBlockStatus.HELD || !studentId.equals(this.heldBy)) {
      return false;
    }

    this.status = HourBlockStatus.AVAILABLE;
    this.heldBy = null;
    this.heldUntil = null;
    return true;
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
   * Whether this student may confirm this hour now.
   *
   * <p>Confirming turns the student's hold into a booking, so it needs a hold that belongs to them
   * and has not run out. A free hour is not enough: without the hold nothing guaranteed the student
   * that the hour was still theirs while they were writing.
   *
   * @throws HourUnavailable when the hour has started, is booked or withdrawn, or another student
   *     holds it
   * @throws HoldExpired when the student does not hold it, or held it and ran out of time
   */
  public void checkBookableBy(UUID studentId, Instant now) {
    Objects.requireNonNull(studentId, "studentId must not be null");
    Objects.requireNonNull(now, "now must not be null");

    if (hasStartedAt(now)) {
      throw new HourUnavailable(STARTED);
    }
    if (isHeldBy(studentId, now)) {
      return;
    }
    switch (this.status) {
      case BOOKED -> throw new HourUnavailable(ALREADY_BOOKED);
      case RELEASED -> throw new HourUnavailable(NO_LONGER_OFFERED);
      case HELD -> {
        if (!isHoldExpired(now)) {
          throw new HourUnavailable(HELD_BY_ANOTHER);
        }
        throw new HoldExpired(NOT_HELD);
      }
      case AVAILABLE -> throw new HoldExpired(NOT_HELD);
    }
  }

  /**
   * Confirms the hour against a booking, turning the student's hold into it.
   *
   * @throws HourUnavailable when the hour cannot be taken any more
   * @throws HoldExpired when the student is not holding it
   * @see #checkBookableBy(UUID, Instant)
   */
  public void book(UUID bookingId, UUID studentId, Instant now) {
    Objects.requireNonNull(bookingId, "bookingId must not be null");

    checkBookableBy(studentId, now);

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

  /** Whether this student holds the hour and their hold has not run out. */
  public boolean isHeldBy(UUID studentId, Instant now) {
    return this.status == HourBlockStatus.HELD
        && studentId.equals(this.heldBy)
        && !isHoldExpired(now);
  }

  /** Whether the hour has begun: from that moment it can no longer be held or booked. */
  public boolean hasStartedAt(Instant now) {
    return !now.isBefore(this.startsAt);
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
