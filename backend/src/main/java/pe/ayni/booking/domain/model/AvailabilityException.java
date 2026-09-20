package pe.ayni.booking.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Objects;
import java.util.UUID;

/**
 * A date specific deviation from a tutor's recurring availability.
 *
 * <ul>
 *   <li><b>REMOVE</b> takes away availability the patterns would otherwise give. Without times it
 *       covers the whole day; with them it covers that window.
 *   <li><b>ADD</b> introduces extraordinary availability outside the recurring hours of that date,
 *       and always carries times.
 * </ul>
 *
 * <p>Block generation applies these over the patterns before materialising {@code
 * booking.hour_blocks}.
 *
 * <p>It is not a Java exception despite the name, which is the table's. It is an exception to a
 * schedule.
 */
@Entity
@Table(schema = "booking", name = "availability_exceptions")
public class AvailabilityException {

  @Id
  @Column(name = "id", nullable = false, updatable = false)
  private UUID id;

  @Column(name = "tenant_id", length = 32, nullable = false, updatable = false)
  private String tenantId;

  @Column(name = "tutor_id", nullable = false, updatable = false)
  private UUID tutorId;

  @Column(name = "exception_date", nullable = false)
  private LocalDate exceptionDate;

  @Column(name = "starts_at_time")
  private LocalTime startsAtTime;

  @Column(name = "ends_at_time")
  private LocalTime endsAtTime;

  @Enumerated(EnumType.STRING)
  @Column(name = "kind", length = 8, nullable = false)
  private ExceptionKind kind;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  protected AvailabilityException() {
    // Required by JPA
  }

  /**
   * @param startsAtTime and {@code endsAtTime}: both, or neither for a whole day REMOVE
   * @throws BookingRuleViolation when the times do not match what the kind allows
   */
  public AvailabilityException(
      UUID id,
      String tenantId,
      UUID tutorId,
      LocalDate exceptionDate,
      LocalTime startsAtTime,
      LocalTime endsAtTime,
      ExceptionKind kind,
      Instant now) {
    this.id = Objects.requireNonNull(id, "id must not be null");
    this.tenantId = Objects.requireNonNull(tenantId, "tenantId must not be null");
    this.tutorId = Objects.requireNonNull(tutorId, "tutorId must not be null");
    this.exceptionDate = Objects.requireNonNull(exceptionDate, "exceptionDate must not be null");
    this.kind = Objects.requireNonNull(kind, "kind must not be null");
    this.startsAtTime = startsAtTime;
    this.endsAtTime = endsAtTime;
    this.createdAt = Objects.requireNonNull(now, "now must not be null");

    validateInvariants();
  }

  private void validateInvariants() {
    if (this.kind == ExceptionKind.ADD && !coversAWindow()) {
      throw new BookingRuleViolation("An ADD exception requires starts_at_time and ends_at_time");
    }
    // Half a window is not a window, and everything downstream would have to guess what the
    // missing end meant. A whole day REMOVE says so by carrying no times at all.
    if ((this.startsAtTime == null) != (this.endsAtTime == null)) {
      throw new BookingRuleViolation(
          "An exception carries both starts_at_time and ends_at_time, or neither");
    }
    if (coversAWindow() && !this.endsAtTime.isAfter(this.startsAtTime)) {
      throw new BookingRuleViolation("ends_at_time must be strictly after starts_at_time");
    }
  }

  /**
   * Whether this exception runs through the given block.
   *
   * <p>Both sides are half open ranges of minutes past midnight, not {@link LocalTime}: a block
   * starting at 23:00 ends at 24:00, and there is no LocalTime for that.
   *
   * <p>Overlapping is enough. A tutor who removes half ten to half eleven has said they cannot be
   * there then, and an hour running through the middle of that cannot be offered. An exception
   * covering the whole day affects everything.
   */
  public boolean affects(int blockStartMinute, int blockEndMinute) {
    if (!coversAWindow()) {
      return true;
    }
    return minutesOf(startsAtTime) < blockEndMinute
        && blockStartMinute < minutesOf(endsAtTime);
  }

  private static int minutesOf(LocalTime time) {
    return time.getHour() * 60 + time.getMinute();
  }

  /** Whether this exception is about a window of the day rather than the whole of it. */
  public boolean coversAWindow() {
    return this.startsAtTime != null && this.endsAtTime != null;
  }

  /** Whether this is a REMOVE with no times, which takes the whole day away. */
  public boolean removesTheWholeDay() {
    return this.kind == ExceptionKind.REMOVE && !coversAWindow();
  }

  /** Removes availability for the whole of a date. */
  public static AvailabilityException removeWholeDay(
      UUID id, String tenantId, UUID tutorId, LocalDate exceptionDate, Instant now) {
    return new AvailabilityException(
        id, tenantId, tutorId, exceptionDate, null, null, ExceptionKind.REMOVE, now);
  }

  /** Removes availability for one window of a date. */
  public static AvailabilityException removeSlot(
      UUID id,
      String tenantId,
      UUID tutorId,
      LocalDate exceptionDate,
      LocalTime startsAtTime,
      LocalTime endsAtTime,
      Instant now) {
    return new AvailabilityException(
        id, tenantId, tutorId, exceptionDate, startsAtTime, endsAtTime, ExceptionKind.REMOVE, now);
  }

  /** Adds extraordinary availability to a date. */
  public static AvailabilityException addSlot(
      UUID id,
      String tenantId,
      UUID tutorId,
      LocalDate exceptionDate,
      LocalTime startsAtTime,
      LocalTime endsAtTime,
      Instant now) {
    return new AvailabilityException(
        id, tenantId, tutorId, exceptionDate, startsAtTime, endsAtTime, ExceptionKind.ADD, now);
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

  public LocalDate getExceptionDate() {
    return exceptionDate;
  }

  public LocalTime getStartsAtTime() {
    return startsAtTime;
  }

  public LocalTime getEndsAtTime() {
    return endsAtTime;
  }

  public ExceptionKind getKind() {
    return kind;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }
}
