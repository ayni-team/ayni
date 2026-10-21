package pe.ayni.booking.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * A session the student did not come to (US34).
 *
 * <p>Kept as a fact per booking rather than as a counter, so each absence can be audited and the
 * same event delivered twice is one row. A tutor is only ever shown how many there are.
 */
@Entity
@Table(schema = "booking", name = "student_absences")
public class StudentAbsence {

  @Id
  @Column(name = "id", nullable = false, updatable = false)
  private UUID id;

  @Column(name = "tenant_id", length = 32, nullable = false, updatable = false)
  private String tenantId;

  @Column(name = "student_id", nullable = false, updatable = false)
  private UUID studentId;

  @Column(name = "booking_id", nullable = false, updatable = false)
  private UUID bookingId;

  @Column(name = "recorded_at", nullable = false, updatable = false)
  private Instant recordedAt;

  protected StudentAbsence() {
    // Required by JPA
  }

  public StudentAbsence(
      UUID id, String tenantId, UUID studentId, UUID bookingId, Instant recordedAt) {
    this.id = Objects.requireNonNull(id, "id must not be null");
    this.tenantId = Objects.requireNonNull(tenantId, "tenantId must not be null");
    this.studentId = Objects.requireNonNull(studentId, "studentId must not be null");
    this.bookingId = Objects.requireNonNull(bookingId, "bookingId must not be null");
    this.recordedAt = Objects.requireNonNull(recordedAt, "recordedAt must not be null");
  }
}
