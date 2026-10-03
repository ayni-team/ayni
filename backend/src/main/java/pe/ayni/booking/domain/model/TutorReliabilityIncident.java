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

/** An auditable incident recorded against a tutor. */
@Entity
@Table(schema = "booking", name = "tutor_reliability")
public class TutorReliabilityIncident {

  @Id
  @Column(name = "id", nullable = false, updatable = false)
  private UUID id;

  @Column(name = "tenant_id", length = 32, nullable = false, updatable = false)
  private String tenantId;

  @Column(name = "tutor_id", nullable = false, updatable = false)
  private UUID tutorId;

  @Column(name = "booking_id", nullable = false, updatable = false)
  private UUID bookingId;

  @Enumerated(EnumType.STRING)
  @Column(name = "kind", length = 24, nullable = false, updatable = false)
  private ReliabilityIncidentKind kind;

  @Column(name = "occurred_at", nullable = false, updatable = false)
  private Instant occurredAt;

  protected TutorReliabilityIncident() {
    // Required by JPA
  }

  public TutorReliabilityIncident(
      UUID id,
      String tenantId,
      UUID tutorId,
      UUID bookingId,
      ReliabilityIncidentKind kind,
      Instant occurredAt) {
    this.id = Objects.requireNonNull(id, "id must not be null");
    this.tenantId = Objects.requireNonNull(tenantId, "tenantId must not be null");
    this.tutorId = Objects.requireNonNull(tutorId, "tutorId must not be null");
    this.bookingId = Objects.requireNonNull(bookingId, "bookingId must not be null");
    this.kind = Objects.requireNonNull(kind, "kind must not be null");
    this.occurredAt = Objects.requireNonNull(occurredAt, "occurredAt must not be null");
  }
}
