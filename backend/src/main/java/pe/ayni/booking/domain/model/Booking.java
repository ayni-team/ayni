package pe.ayni.booking.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import pe.ayni.booking.BookingStatus;
import pe.ayni.shared.domain.Credits;

/**
 * A reservation of one or more consecutive hours with the same tutor.
 *
 * <p>It only ever comes into existence confirmed: the credits were charged and the hours were
 * marked in the same transaction that saves it. There is no pending booking, because a booking
 * that might still fail is what the hold on the hours is for.
 *
 * <p>The price is not a parameter. One credit is one hour, so what a booking charges is decided
 * here from the hours it covers, the same rule {@code ck_bookings_pricing} enforces in the
 * database.
 */
@Entity
@Table(schema = "booking", name = "bookings")
public class Booking {

  @Id
  @Column(name = "id", nullable = false, updatable = false)
  private UUID id;

  @Column(name = "tenant_id", length = 32, nullable = false, updatable = false)
  private String tenantId;

  @Column(name = "student_id", nullable = false, updatable = false)
  private UUID studentId;

  @Column(name = "tutor_id", nullable = false, updatable = false)
  private UUID tutorId;

  @Column(name = "catalog_item_id", nullable = false, updatable = false)
  private UUID catalogItemId;

  @Column(name = "starts_at", nullable = false, updatable = false)
  private Instant startsAt;

  @Column(name = "ends_at", nullable = false, updatable = false)
  private Instant endsAt;

  @Column(name = "hours", nullable = false, updatable = false)
  private short hours;

  @Column(name = "credits_charged", nullable = false, updatable = false)
  private int creditsCharged;

  @Column(name = "need_description", nullable = false, updatable = false, columnDefinition = "text")
  private String needDescription;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", length = 16, nullable = false)
  private BookingStatus status;

  @Enumerated(EnumType.STRING)
  @Column(name = "cancelled_by", length = 16)
  private CancelledBy cancelledBy;

  @Column(name = "cancelled_at")
  private Instant cancelledAt;

  @Column(name = "cancelled_late")
  private Boolean cancelledLate;

  @Column(name = "cancellation_reason", length = 500)
  private String cancellationReason;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  protected Booking() {
    // Required by JPA
  }

  private Booking(
      UUID id,
      String tenantId,
      UUID studentId,
      UUID tutorId,
      UUID catalogItemId,
      Instant startsAt,
      Instant endsAt,
      int hours,
      String needDescription,
      Instant now) {
    this.id = Objects.requireNonNull(id, "id must not be null");
    this.tenantId = Objects.requireNonNull(tenantId, "tenantId must not be null");
    this.studentId = Objects.requireNonNull(studentId, "studentId must not be null");
    this.tutorId = Objects.requireNonNull(tutorId, "tutorId must not be null");
    this.catalogItemId = Objects.requireNonNull(catalogItemId, "catalogItemId must not be null");
    this.startsAt = Objects.requireNonNull(startsAt, "startsAt must not be null");
    this.endsAt = Objects.requireNonNull(endsAt, "endsAt must not be null");
    Objects.requireNonNull(needDescription, "needDescription must not be null");
    Objects.requireNonNull(now, "now must not be null");

    if (studentId.equals(tutorId)) {
      throw new BookingRuleViolation("A student cannot book their own hours");
    }
    if (hours < 1 || hours > Short.MAX_VALUE) {
      throw new BookingRuleViolation("A booking covers at least one hour");
    }
    if (!endsAt.isAfter(startsAt)) {
      throw new BookingRuleViolation("ends_at must be strictly after starts_at");
    }
    if (needDescription.isBlank()) {
      // The tutor reads it before the session: without it they arrive unprepared.
      throw new BookingRuleViolation("Describe what you need help with");
    }

    this.hours = (short) hours;
    this.creditsCharged = hours;
    this.needDescription = needDescription.strip();
    this.status = BookingStatus.CONFIRMED;
    this.createdAt = now;
    this.updatedAt = now;
  }

  /**
   * A booking over hours that have just been charged and taken.
   *
   * @param startsAt the start of the first hour
   * @param endsAt the end of the last hour
   * @param hours how many one hour blocks it covers, which is also what it costs
   * @throws BookingRuleViolation when the student is the tutor, there are no hours, the range is
   *     backwards or the need is not described
   */
  public static Booking confirm(
      UUID id,
      String tenantId,
      UUID studentId,
      UUID tutorId,
      UUID catalogItemId,
      Instant startsAt,
      Instant endsAt,
      int hours,
      String needDescription,
      Instant now) {
    return new Booking(
        id, tenantId, studentId, tutorId, catalogItemId, startsAt, endsAt, hours, needDescription,
        now);
  }

  /** What this booking costs: one credit per hour. */
  public Credits price() {
    return Credits.of(creditsCharged);
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

  public UUID getTutorId() {
    return tutorId;
  }

  public UUID getCatalogItemId() {
    return catalogItemId;
  }

  public Instant getStartsAt() {
    return startsAt;
  }

  public Instant getEndsAt() {
    return endsAt;
  }

  public int getHours() {
    return hours;
  }

  public int getCreditsCharged() {
    return creditsCharged;
  }

  public String getNeedDescription() {
    return needDescription;
  }

  public BookingStatus getStatus() {
    return status;
  }

  public CancelledBy getCancelledBy() {
    return cancelledBy;
  }

  public Instant getCancelledAt() {
    return cancelledAt;
  }

  public Boolean getCancelledLate() {
    return cancelledLate;
  }

  public String getCancellationReason() {
    return cancellationReason;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }
}
